package dev.vetki

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.vetki.data.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CanvasStorageTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var db: VetkiDatabase

    @Before
    fun before() {
        db = Room.inMemoryDatabaseBuilder(context, VetkiDatabase::class.java).build()
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun connectionsSurviveBackupAndTemplateCopy() = runBlocking {
        val board = Board(title = "Связи")
        db.dao().save(board)
        val a = Card(boardId = board.id, title = "Откуда")
        val b = Card(boardId = board.id, title = "Куда", x = 300f)
        val c = Card(boardId = board.id, title = "Ещё", y = 300f)
        val linked =
            a.copy(
                connections =
                    CardJson.connectionsJson(
                        listOf(
                            dev.vetki.core.CardConnection(b.id, "right"),
                            dev.vetki.core.CardConnection(c.id, "bottom"),
                        )
                    )
            )
        listOf(linked, b, c).forEach { db.dao().save(it) }
        val other = Room.inMemoryDatabaseBuilder(context, VetkiDatabase::class.java).build()
        try {
            Backup.restore(other, Backup.export(db))
            assertEquals(linked, other.dao().card(a.id))
        } finally {
            other.close()
        }
        val template = db.dao().saveAsTemplate(board, "Со стрелками")
        val copy = db.dao().fromTemplate("Копия", template.id)
        val copied = db.dao().allCards().filter { it.boardId == copy.id }
        val arrows = CardJson.connections(copied.single { it.title == "Откуда" }.connections)
        assertEquals(2, arrows.size)
        assertTrue(arrows.all { arrow -> copied.any { it.id == arrow.targetId } })
        assertTrue(arrows.none { it.targetId == b.id || it.targetId == c.id })
    }

    @Test
    fun calculatorDraftSurvivesStorageBackupAndTemplate() = runBlocking {
        val board = Board(title = "Calculator test")
        db.dao().save(board)
        val draft =
            CalculationDraft(
                mode = "salary",
                monthly = true,
                pay = "60000",
                worked = "10",
                planned = "20",
                bonus = "2500",
                deduction = "10",
            )
        val card =
            Card(
                boardId = board.id,
                title = "Pay",
                kind = "calculator",
                body = "29250",
                calculation = CalculationJson.encode(draft),
            )
        db.dao().save(card)
        assertEquals(draft, CalculationJson.decode(db.dao().card(card.id)!!.calculation))
        val template = db.dao().saveAsTemplate(board, "Calculation")
        val copy = db.dao().fromTemplate("Copy", template.id)
        assertEquals(
            draft,
            CalculationJson.decode(db.dao().allCards().single { it.boardId == copy.id }.calculation),
        )
        val other = Room.inMemoryDatabaseBuilder(context, VetkiDatabase::class.java).build()
        try {
            Backup.restore(other, Backup.export(db))
            assertEquals(card, other.dao().card(card.id))
            assertEquals(
                draft,
                CalculationJson.decode(
                    other.dao().allCards().single { it.boardId == copy.id }.calculation
                ),
            )
        } finally {
            other.close()
        }
    }

    @Test
    fun invalidCalculatorBackupDoesNotWritePartialData() = runBlocking {
        val board = Board(title = "Invalid calculator")
        db.dao().save(board)
        db.dao()
            .save(
                Card(
                    boardId = board.id,
                    title = "Invalid",
                    kind = "calculator",
                    calculation = "{\"mode\":\"unexpected\"}",
                )
            )
        val other = Room.inMemoryDatabaseBuilder(context, VetkiDatabase::class.java).build()
        try {
            assertTrue(runCatching { Backup.restore(other, Backup.export(db)) }.isFailure)
            assertTrue(other.dao().allBoards().isEmpty())
        } finally {
            other.close()
        }
    }

    @Test
    fun legacyVersionTwoBackupStillRestoresWithoutCalculationField() = runBlocking {
        val board = Board(title = "Legacy")
        val card = Card(boardId = board.id, title = "Existing text", body = "Keep me")
        db.dao().save(board)
        db.dao().save(card)
        val payload = JSONObject(Backup.export(db)).put("version", 2)
        payload.getJSONArray("cards").getJSONObject(0).remove("calculation")
        val other = Room.inMemoryDatabaseBuilder(context, VetkiDatabase::class.java).build()
        try {
            Backup.restore(other, payload.toString())
            assertEquals(card, other.dao().card(card.id))
        } finally {
            other.close()
        }
    }

    @Test
    fun emptyCanvasReallyStartsEmpty() = runBlocking {
        db.dao().createBoard("Чистый лист", "empty")
        assertTrue(db.dao().allCards().isEmpty())
    }

    @Test
    fun workTemplateHasPracticalQuestions() = runBlocking {
        val board = db.dao().createBoard("Работа", "work")
        val cards = db.dao().allCards()
        assertEquals(8, cards.size)
        assertTrue(
            cards.any {
                it.title == dev.vetki.i18n.tr(dev.vetki.i18n.R.string.next_step) &&
                    it.body.isNotBlank()
            }
        )
        assertEquals(7, cards.count { it.parentId != null && it.boardId == board.id })
    }

    @Test
    fun customTemplateCopiesIdsAndResetsCompletionWithoutReminders() = runBlocking {
        val board = db.dao().createBoard("Моя работа", "work")
        val original = db.dao().allCards().first()
        db.dao().save(original.copy(done = true, status = 3))
        db.dao()
            .save(
                Reminder(
                    title = "Старый срок",
                    cardId = original.id,
                    boardId = board.id,
                    at = 12345678L,
                )
            )
        val template = db.dao().saveAsTemplate(board, "Мой процесс")
        val copy = db.withTransaction { db.dao().fromTemplate("Новая работа", template.id) }
        val cards = db.dao().allCards().filter { it.boardId == copy.id }
        assertEquals(8, cards.size)
        val sourceIds = db.dao().allCards().filter { it.boardId == board.id }.map { it.id }.toSet()
        assertTrue(cards.none { it.id in sourceIds || it.done || it.status == 3 })
        assertTrue(
            cards.all { it.parentId == null || cards.any { other -> other.id == it.parentId } }
        )
        assertFalse(db.dao().allReminders().any { it.boardId == copy.id })
    }

    @Test
    fun backupPreservesGroupsOptionsAndTemplates() = runBlocking {
        val board = Board(title = "Холст", snapGrid = true, snapObjects = true, readOnly = true)
        db.dao().save(board)
        val group =
            Card(boardId = board.id, title = "Группа", kind = "group", width = 440f, height = 300f)
        db.dao().save(group)
        val child = Card(boardId = board.id, title = "Внутри", groupId = group.id, x = 20f, y = 50f)
        db.dao().save(child)
        val template = db.dao().saveAsTemplate(board, "Шаблон")
        val other = Room.inMemoryDatabaseBuilder(context, VetkiDatabase::class.java).build()
        try {
            Backup.restore(other, Backup.export(db))
            assertEquals(board, other.dao().board(board.id))
            assertEquals(child, other.dao().card(child.id))
            assertEquals(template, other.dao().template(template.id))
        } finally {
            other.close()
        }
    }

    @Test
    fun attachmentBackupRestoresTheActualFile() = runBlocking {
        val store = AttachmentStore(context)
        val uri = "attachment://${java.util.UUID.randomUUID()}.txt"
        val file = store.file(uri)
        try {
            file.writeText("Вложение из резервной копии")
            val board = Board(title = "Файл")
            db.dao().save(board)
            val card =
                Card(
                    boardId = board.id,
                    title = "Текст",
                    kind = "file",
                    uri = uri,
                    mime = "text/plain",
                )
            db.dao().save(card)
            val backup = Backup.export(db, store)
            assertTrue(file.delete())
            Backup.restore(db, backup, store)
            assertEquals("Вложение из резервной копии", file.readText())
        } finally {
            file.delete()
        }
    }

    @Test
    fun migrationFromVersionTwoKeepsExistingUserData() = runBlocking {
        val name = "qa-schema-migration.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile!!.mkdirs()
        val schema =
            JSONObject(
                    InstrumentationRegistry.getInstrumentation()
                        .context
                        .assets
                        .open("schema-v2.json")
                        .bufferedReader()
                        .use { it.readText() }
                )
                .getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indices.length()) old.execSQL(
                    indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table)
                )
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL(
                "INSERT INTO notes (id,parentId,title,body,folder,updated) VALUES ('keep',NULL,'Моя заметка','Не потерять',0,123)"
            )
            old.execSQL(
                "INSERT INTO boards (id,title,template,updated) VALUES ('board','Моё дерево','empty',123)"
            )
            old.execSQL(
                "INSERT INTO cards (id,boardId,parentId,title,body,x,y,status,done) VALUES ('card','board',NULL,'Карточка','Текст',41,73,0,0)"
            )
            old.version = 2
        }
        val migrated =
            Room.databaseBuilder(context, VetkiDatabase::class.java, name)
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
        try {
            assertEquals("Не потерять", migrated.dao().note("keep")!!.body)
            assertEquals(41f, migrated.dao().card("card")!!.x, .01f)
            assertEquals("text", migrated.dao().card("card")!!.kind)
            assertNull(migrated.dao().card("card")!!.calculation)
            assertFalse(migrated.dao().board("board")!!.readOnly)
            assertTrue(migrated.dao().allTemplates().isEmpty())
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }
}
