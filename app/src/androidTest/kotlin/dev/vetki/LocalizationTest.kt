package dev.vetki

import android.content.res.Configuration
import android.os.LocaleList
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.vetki.data.VetkiDatabase
import dev.vetki.data.createBoard
import dev.vetki.i18n.Language
import dev.vetki.i18n.R as StringsRes
import dev.vetki.i18n.Strings
import dev.vetki.i18n.tr
import dev.vetki.ui.dateTime
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalizationTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var previous: String

    @Before
    fun before() {
        previous = Language.selected(context)
    }

    @After
    fun after() {
        Language.set(context, previous)
    }

    private fun select(language: String) {
        Language.set(context, language)
        val deadline = System.currentTimeMillis() + 3000
        while (
            Language.selected(context) != language && System.currentTimeMillis() < deadline
        ) Thread.sleep(20)
        assertEquals(language, Language.selected(context))
    }

    @Test
    fun bothCatalogsResolveAndFormatEveryString() {
        val english =
            context.createConfigurationContext(
                Configuration(context.resources.configuration).apply {
                    setLocales(LocaleList.forLanguageTags("en"))
                }
            )
        val russian =
            context.createConfigurationContext(
                Configuration(context.resources.configuration).apply {
                    setLocales(LocaleList.forLanguageTags("ru"))
                }
            )
        val slot = Regex("%(\\d+)\\\$s")
        val fields =
            StringsRes.string::class.java.declaredFields.filter {
                it.type == Int::class.javaPrimitiveType
            }
        assertTrue(fields.size >= 322)
        fields.forEach { field ->
            val id = field.getInt(null)
            val en = english.getString(id)
            val ru = russian.getString(id)
            assertTrue(field.name, en.isNotBlank() && ru.isNotBlank())
            assertEquals(
                field.name,
                slot.findAll(en).map { it.value }.toList().sorted(),
                slot.findAll(ru).map { it.value }.toList().sorted(),
            )
            val count = slot.findAll(en).maxOfOrNull { it.groupValues[1].toInt() } ?: 0
            if (count > 0) {
                val args = Array<Any>(count) { "check" }
                assertFalse(field.name, slot.containsMatchIn(english.getString(id, *args)))
                assertFalse(field.name, slot.containsMatchIn(russian.getString(id, *args)))
            }
            if (field.name != "language_russian")
                assertFalse(field.name, Regex("[А-Яа-яЁё]").containsMatchIn(en))
            if (field.name !in setOf("language_russian", "language_english"))
                assertNotEquals(field.name, en, ru)
        }
    }

    @Test
    fun interfaceDatesAndSoundActionsFollowSelectedLanguage() {
        select("en")
        assertEquals("Notes", tr(StringsRes.string.notes))
        assertEquals("Stop", tr(StringsRes.string.stop))
        assertEquals("Sound volume: 42%", tr(StringsRes.string.sound_volume, 42))
        assertTrue(dateTime(Instant.parse("2026-10-24T12:00:00Z").toEpochMilli()).contains("Oct"))
        select("ru")
        assertEquals("Заметки", tr(StringsRes.string.notes))
        assertEquals("Остановить", tr(StringsRes.string.stop))
        assertEquals("Громкость мелодии: 42%", tr(StringsRes.string.sound_volume, 42))
        assertTrue(dateTime(Instant.parse("2026-10-24T12:00:00Z").toEpochMilli()).contains("окт"))
    }

    @Test
    fun englishTemplatesAreCreatedInEnglishAndExistingCardsStayUnchanged() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, VetkiDatabase::class.java).build()
        try {
            select("en")
            db.dao().createBoard("Work", "work")
            val saved = db.dao().allCards()
            assertEquals(8, saved.size)
            assertTrue(saved.any { it.title == "Next step" && it.body.contains("today") })
            select("ru")
            assertEquals(saved.toSet(), db.dao().allCards().toSet())
            db.dao().createBoard("Работа", "work")
            assertTrue(db.dao().allCards().any { it.title == "Ближайший шаг" })
        } finally {
            db.close()
        }
    }

    @Test
    fun languageSurvivesResourceReinitialization() {
        select("en")
        Strings.initialize(context)
        assertEquals("en", Language.selected(context))
        assertEquals("en", Strings.locale.language)
        assertEquals("Calendar", tr(StringsRes.string.calendar))
    }

    @Test
    fun systemDefaultCanBeRestored() {
        select("en")
        select(Language.SYSTEM)
        assertEquals(Language.SYSTEM, Language.selected(context))
        assertEquals(
            Language.locale(context).language,
            Language.wrap(context).resources.configuration.locales[0].language,
        )
    }
}
