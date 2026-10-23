package dev.vetki

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.vetki.data.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StorageTest {
    private lateinit var db: VetkiDatabase

    @Before
    fun setUp() {
        db =
            Room.inMemoryDatabaseBuilder(
                    InstrumentationRegistry.getInstrumentation().targetContext,
                    VetkiDatabase::class.java,
                )
                .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun newWorkspaceIsEmpty() = runBlocking {
        assertTrue(db.dao().allNotes().isEmpty())
        assertTrue(db.dao().allBoards().isEmpty())
        assertTrue(db.dao().allCards().isEmpty())
        assertTrue(db.dao().allReminders().isEmpty())
    }

    @Test
    fun bundledMelodyIsPlayable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = context.resources.getIdentifier("calm_reminder", "raw", context.packageName)
        assertTrue(id != 0)
        val metadata = android.media.MediaMetadataRetriever()
        try {
            context.resources.openRawResourceFd(id).use { descriptor ->
                metadata.setDataSource(
                    descriptor.fileDescriptor,
                    descriptor.startOffset,
                    descriptor.length,
                )
            }
            val duration =
                metadata
                    .extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)!!
                    .toLong()
            assertTrue(duration in 9900L..10200L)
        } finally {
            metadata.release()
        }
    }

    @Test
    fun noteSurvivesDatabaseReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "qa-persistence.db"
        context.deleteDatabase(name)
        val note = Note(title = "Сохранённая заметка", body = "Текст после перезапуска")
        try {
            val first = Room.databaseBuilder(context, VetkiDatabase::class.java, name).build()
            try {
                first.dao().save(note)
            } finally {
                first.close()
            }
            val second = Room.databaseBuilder(context, VetkiDatabase::class.java, name).build()
            try {
                assertEquals(note, second.dao().allNotes().single())
            } finally {
                second.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test
    fun travelTemplateCreatesConnectedTree() = runBlocking {
        val board = db.dao().createBoard("Япония — 旅行", "travel")
        val cards = db.dao().allCards()
        assertEquals(7, cards.size)
        val root = cards.single { it.parentId == null }
        assertEquals(board.title, root.title)
        assertEquals(6, cards.count { it.parentId == root.id })
    }

    @Test
    fun backupRoundTripPreservesHierarchyCoordinatesAndReminder() = runBlocking {
        val board = db.dao().createBoard("Поездка", "travel")
        val card = db.dao().allCards().first().copy(x = -123.5f, y = 980f, done = true)
        db.dao().save(card)
        val parent = Note(title = "Личное", folder = true)
        db.dao().save(parent)
        val note =
            Note(title = "Документы 🧳", parentId = parent.id, body = "# Паспорт\n- [ ] Проверить")
        db.dao().save(note)
        val reminder =
            Reminder(
                title = "Проверить паспорт",
                cardId = card.id,
                boardId = board.id,
                at = System.currentTimeMillis() + 3600000,
                nag = true,
                alarm = true,
                repeat = "weekly",
            )
        db.dao().save(reminder)
        val text = Backup.export(db)
        val other =
            Room.inMemoryDatabaseBuilder(
                    InstrumentationRegistry.getInstrumentation().targetContext,
                    VetkiDatabase::class.java,
                )
                .build()
        try {
            Backup.restore(other, text)
            assertEquals(note, other.dao().allNotes().first { it.id == note.id })
            assertEquals(card, other.dao().card(card.id))
            assertEquals(reminder, other.dao().reminder(reminder.id))
        } finally {
            other.close()
        }
    }

    @Test
    fun invalidBackupDoesNotChangeExistingData() = runBlocking {
        db.dao().save(Note(id = "existing", title = "Не потерять"))
        val text = JSONObject(Backup.export(db))
        val notes = text.getJSONArray("notes")
        notes.getJSONObject(0).put("parentId", "existing")
        var failed = false
        try {
            Backup.restore(db, text.toString())
        } catch (e: IllegalArgumentException) {
            failed = true
        }
        assertTrue(failed)
        assertNull(db.dao().allNotes().single().parentId)
    }

    @Test
    fun deletingBoardCascadesToCards() = runBlocking {
        val board = db.dao().createBoard("Проект", "project")
        assertEquals(5, db.dao().allCards().size)
        db.dao().deleteBoard(board.id)
        assertTrue(db.dao().allCards().isEmpty())
    }
}
