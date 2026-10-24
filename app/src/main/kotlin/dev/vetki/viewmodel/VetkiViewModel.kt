package dev.vetki.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import dev.vetki.core.*
import dev.vetki.data.*
import dev.vetki.i18n.R as StringsRes
import dev.vetki.i18n.tr
import dev.vetki.reminders.ReminderScheduler
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AppState(
    val notes: List<Note> = emptyList(),
    val boards: List<Board> = emptyList(),
    val cards: List<Card> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val loaded: Boolean = false,
    val templates: List<BoardTemplate> = emptyList(),
)

class VetkiViewModel(app: Application) : AndroidViewModel(app) {
    val database = VetkiDatabase.get(app)
    val dao = database.dao()
    val scheduler = ReminderScheduler(app)
    val attachments = AttachmentStore(app)
    private val boardLock = Mutex()
    val state =
        combine(dao.notes(), dao.boards(), dao.cards(), dao.reminders(), dao.templates()) {
                n,
                b,
                c,
                r,
                t ->
                AppState(n, b, c, r, true, t)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppState())
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 5)
    private val drafts = mutableMapOf<String, Note>()
    private val jobs = mutableMapOf<String, Job>()

    init {
        launch { scheduler.restore() }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                messages.emit(
                    tr(StringsRes.string.could_not_save, e.message ?: tr(StringsRes.string.error))
                )
            }
        }
    }

    fun saveNote(note: Note) {
        drafts[note.id] = note
        jobs[note.id]?.cancel()
        jobs[note.id] =
            viewModelScope.launch {
                delay(350)
                dao.save(note.copy(updated = System.currentTimeMillis()))
                drafts.remove(note.id)
                jobs.remove(note.id)
            }
    }

    fun flush() {
        drafts.toMap().forEach { (id, n) ->
            jobs.remove(id)?.cancel()
            launch { dao.save(n.copy(updated = System.currentTimeMillis())) }
            drafts.remove(id)
        }
    }

    suspend fun flushPending() {
        val pending =
            withContext(Dispatchers.Main.immediate) {
                drafts.values.toList().also { values ->
                    values.forEach {
                        jobs.remove(it.id)?.cancel()
                        drafts.remove(it.id)
                    }
                }
            }
        withContext(Dispatchers.IO) {
            pending.forEach { dao.save(it.copy(updated = System.currentTimeMillis())) }
        }
    }

    fun createNote(parent: String?, folder: Boolean, title: String, done: (String) -> Unit = {}) =
        launch {
            val note =
                Note(
                    parentId = parent,
                    title =
                        title.trim().ifBlank {
                            if (folder) tr(StringsRes.string.new_folder)
                            else tr(StringsRes.string.new_note)
                        },
                    folder = folder,
                )
            dao.save(note)
            done(note.id)
        }

    fun deleteNote(note: Note) = launch {
        val ids =
            descendants(
                dao.allNotes().map { TreeEntry(it.id, it.parentId, it.title, it.folder) },
                note.id,
            )
        ids.forEach {
            drafts.remove(it)
            jobs.remove(it)?.cancel()
        }
        dao.deleteNotes(ids.toList())
    }

    fun moveNote(note: Note, parent: String?) = launch {
        val forbidden =
            descendants(
                dao.allNotes().map { TreeEntry(it.id, it.parentId, it.title, it.folder) },
                note.id,
            )
        require(parent !in forbidden) {
            tr(StringsRes.string.a_folder_cannot_be_moved_inside_itself)
        }
        dao.save(note.copy(parentId = parent))
    }

    fun createBoard(title: String, template: String, done: (String) -> Unit) = launch {
        database
            .withTransaction {
                if (template.startsWith("saved:"))
                    dao.fromTemplate(title, template.removePrefix("saved:"))
                else
                    dao.createBoard(
                        title.trim().ifBlank { tr(StringsRes.string.new_canvas) },
                        template,
                    )
            }
            .let { done(it.id) }
    }

    fun saveBoard(board: Board) = launch { dao.save(board) }

    fun deleteBoard(board: Board) = launch {
        database.withTransaction {
            dao.allReminders()
                .filter { it.boardId == board.id }
                .forEach {
                    scheduler.cancel(it.id)
                    dao.deleteReminder(it.id)
                }
            dao.deleteBoard(board.id)
        }
    }

    fun saveCard(card: Card) = launch {
        require(dao.board(card.boardId)?.readOnly != true) {
            tr(StringsRes.string.this_canvas_is_read_only)
        }
        dao.save(card.copy(connections = dao.card(card.id)?.connections ?: card.connections))
        dao.allReminders()
            .filter { it.cardId == card.id }
            .forEach { r ->
                if (card.done) scheduler.complete(r)
                else if (r.title != card.title) dao.save(r.copy(title = card.title))
            }
    }

    fun addContent(
        board: String,
        point: WorldPoint? = null,
        kind: String = "text",
        title: String = tr(StringsRes.string.new_card),
        body: String = "",
        noteId: String? = null,
        uri: String? = null,
        mime: String? = null,
        calculation: String? = null,
        done: (String) -> Unit = {},
    ) = launch {
        boardLock.withLock {
            val settings = requireNotNull(dao.board(board))
            require(!settings.readOnly) { tr(StringsRes.string.turn_off_read_only_mode_first) }
            val all = dao.allCards().filter { it.boardId == board }
            val width = if (kind == "group") 440f else if (kind == "image") 220f else 190f
            val height = if (kind == "group") 300f else if (kind == "image") 190f else 112f
            val origin = point ?: WorldPoint(0f, 0f)
            val draft =
                Card(
                    boardId = board,
                    title = title,
                    body = body,
                    kind = kind,
                    noteId = noteId,
                    uri = uri,
                    mime = mime,
                    calculation = calculation,
                    width = width,
                    height = height,
                )
            val position =
                snapPosition(
                    origin,
                    draft.asCanvas(),
                    all.map { it.asCanvas() },
                    settings.snapGrid,
                    settings.snapObjects,
                )
            val group =
                if (kind == "group") null
                else
                    all.lastOrNull {
                            it.kind == "group" &&
                                it.asCanvas()
                                    .rect()
                                    .contains(
                                        WorldPoint(position.x + width / 2, position.y + height / 2)
                                    )
                        }
                        ?.id
            val card = draft.copy(x = position.x, y = position.y, groupId = group)
            database.withTransaction {
                dao.save(card)
                if (kind == "group")
                    all.filter {
                            it.kind != "group" &&
                                it.groupId == null &&
                                card
                                    .asCanvas()
                                    .rect()
                                    .contains(WorldPoint(it.x + it.width / 2, it.y + it.height / 2))
                        }
                        .forEach { dao.save(it.copy(groupId = card.id)) }
            }
            done(card.id)
        }
    }

    fun connectCards(sourceId: String, side: String, targetId: String) = launch {
        boardLock.withLock {
            database.withTransaction {
                val source = requireNotNull(dao.card(sourceId))
                val target = requireNotNull(dao.card(targetId))
                require(
                    source.id != target.id &&
                        source.boardId == target.boardId &&
                        source.kind != "group" &&
                        target.kind != "group"
                )
                require(dao.board(source.boardId)?.readOnly != true)
                require(side in setOf("top", "bottom", "left", "right"))
                val links =
                    CardJson.connections(source.connections).filterNot { it.targetId == targetId }
                dao.save(
                    source.copy(
                        connections =
                            CardJson.connectionsJson(links + CardConnection(targetId, side))
                    )
                )
            }
        }
    }

    fun moveCard(id: String, x: Float, y: Float) = launch {
        boardLock.withLock {
            database.withTransaction {
                val card = dao.card(id) ?: return@withTransaction
                val board = dao.board(card.boardId) ?: return@withTransaction
                require(!board.readOnly) { tr(StringsRes.string.this_canvas_is_read_only) }
                val others = dao.allCards().filter { it.boardId == card.boardId }
                val position =
                    snapPosition(
                        WorldPoint(x, y),
                        card.asCanvas(),
                        others.map { it.asCanvas() },
                        board.snapGrid,
                        board.snapObjects,
                    )
                if (card.kind == "group") {
                    val members = others.filter { it.groupId == id }
                    val dx = position.x - card.x
                    val dy = position.y - card.y
                    members.forEach { dao.save(it.copy(x = it.x + dx, y = it.y + dy)) }
                    dao.save(card.copy(x = card.x + dx, y = card.y + dy))
                } else {
                    val group =
                        others
                            .lastOrNull {
                                it.kind == "group" &&
                                    it.asCanvas()
                                        .rect()
                                        .contains(
                                            WorldPoint(
                                                position.x + card.width / 2,
                                                position.y + card.height / 2,
                                            )
                                        )
                            }
                            ?.id
                    dao.save(card.copy(x = position.x, y = position.y, groupId = group))
                }
            }
        }
    }

    fun setBoardOption(id: String, option: String, value: Boolean) = launch {
        boardLock.withLock {
            dao.board(id)?.let { board ->
                dao.save(
                    when (option) {
                        "grid" -> board.copy(snapGrid = value)
                        "objects" -> board.copy(snapObjects = value)
                        "readOnly" -> board.copy(readOnly = value)
                        else -> board
                    }
                )
            }
        }
    }

    fun saveTemplate(board: Board, title: String) = launch {
        database.withTransaction { dao.saveAsTemplate(board, title) }
        messages.emit(tr(StringsRes.string.template_saved_available_when_you_create_a_canvas))
    }

    fun deleteTemplate(id: String) = launch { dao.deleteTemplate(id) }

    fun deleteCard(card: Card) = launch {
        database.withTransaction {
            require(dao.board(card.boardId)?.readOnly != true)
            val boardCards = dao.allCards().filter { it.boardId == card.boardId }
            if (card.kind == "group") {
                boardCards
                    .filter { it.groupId == card.id }
                    .forEach { dao.save(it.copy(groupId = null)) }
                dao.deleteCards(listOf(card.id))
                return@withTransaction
            }
            val ids =
                descendants(
                    boardCards.map { TreeEntry(it.id, it.parentId, it.title, false) },
                    card.id,
                )
            dao.allReminders()
                .filter { it.cardId in ids }
                .forEach {
                    scheduler.cancel(it.id)
                    dao.deleteReminder(it.id)
                }
            boardCards
                .filter { it.id !in ids }
                .forEach { other ->
                    val links = CardJson.connections(other.connections)
                    val kept = links.filterNot { it.targetId in ids }
                    if (kept.size != links.size)
                        dao.save(other.copy(connections = CardJson.connectionsJson(kept)))
                }
            dao.deleteCards(ids.toList())
        }
    }

    fun saveReminder(reminder: Reminder) = launch {
        scheduler.cancel(reminder.id)
        dao.save(reminder.copy(done = false, delivered = false, nextAt = null))
        reminder.cardId?.let {
            dao.card(it)?.let { c -> if (c.done) dao.save(c.copy(done = false)) }
        }
        scheduler.schedule(reminder.copy(done = false, delivered = false, nextAt = null))
        messages.emit(
            if (scheduler.exactAvailable()) tr(StringsRes.string.reminder_set)
            else tr(StringsRes.string.saved_without_exact_alarm_access_delivery_may_be_delayed)
        )
    }

    fun completeReminder(reminder: Reminder) = launch { scheduler.complete(reminder) }

    fun deleteReminder(reminder: Reminder) = launch {
        scheduler.cancel(reminder.id)
        dao.deleteReminder(reminder.id)
    }

    fun layoutBoard(board: String) = launch {
        require(dao.board(board)?.readOnly != true) {
            tr(StringsRes.string.this_canvas_is_read_only)
        }
        val cards = dao.allCards().filter { it.boardId == board && it.kind != "group" }
        val children = cards.groupBy { it.parentId }
        val seen = mutableSetOf<String>()
        var row = 0
        suspend fun walk(c: Card, depth: Int) {
            if (!seen.add(c.id)) return
            dao.save(c.copy(x = depth * 240f, y = row++ * 220f))
            children[c.id].orEmpty().forEach { walk(it, depth + 1) }
        }
        database.withTransaction { children[null].orEmpty().forEach { walk(it, 0) } }
    }
}
