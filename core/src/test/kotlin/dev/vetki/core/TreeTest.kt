package dev.vetki.core

import java.time.*
import org.junit.Assert.*
import org.junit.Test

class TreeTest {
    private val entries =
        listOf(
            TreeEntry("root", null, "Личное", true),
            TreeEntry("travel", "root", "Путешествия", true),
            TreeEntry("note", "travel", "Паспорт", false),
            TreeEntry("other", null, "Работа", true),
        )

    @Test
    fun alarmGainStartsQuietAndStopsAtFullVolume() {
        assertEquals(.12f, alarmGain(-1000), .001f)
        assertEquals(.56f, alarmGain(30000), .001f)
        assertEquals(1f, alarmGain(60000), .001f)
        assertEquals(1f, alarmGain(3600000), .001f)
    }

    @Test
    fun alarmGainNeverDecreases() {
        val values = (0L..70000L step 1000).map(::alarmGain)
        assertTrue(values.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test
    fun collapsedFoldersHideChildren() {
        assertEquals(listOf("root", "other"), visibleTree(entries, emptySet()).map { it.entry.id })
    }

    @Test
    fun searchIncludesAncestorsEvenWhenCollapsed() {
        assertEquals(
            listOf("root", "travel", "note"),
            visibleTree(entries, emptySet(), "паспорт").map { it.entry.id },
        )
    }

    @Test
    fun deletingFolderIncludesAllDescendantsOnly() {
        assertEquals(setOf("root", "travel", "note"), descendants(entries, "root"))
    }

    @Test
    fun cyclicInputDoesNotLoop() {
        val cycle = listOf(TreeEntry("a", "b", "A", true), TreeEntry("b", "a", "B", true))
        assertEquals(setOf("a", "b"), descendants(cycle, "a"))
        assertTrue(visibleTree(cycle, emptySet(), "A").isEmpty())
    }

    @Test
    fun dailyRepeatPreservesLocalTimeAcrossDaylightSaving() {
        val zone = ZoneId.of("Europe/Berlin")
        val date = ZonedDateTime.of(2026, 3, 28, 9, 0, 0, 0, zone)
        val next =
            nextOccurrence(
                date.toInstant().toEpochMilli(),
                "daily",
                zone,
                date.toInstant().toEpochMilli(),
            )!!
        assertEquals(9, Instant.ofEpochMilli(next).atZone(zone).hour)
        assertEquals(23 * 60 * 60 * 1000L, next - date.toInstant().toEpochMilli())
    }

    @Test
    fun repeatingTaskSkipsPastOccurrences() {
        val at = Instant.parse("2026-10-01T10:00:00Z").toEpochMilli()
        val now = Instant.parse("2026-10-22T12:00:00Z").toEpochMilli()
        assertEquals(
            Instant.parse("2026-10-29T10:00:00Z").toEpochMilli(),
            nextOccurrence(at, "weekly", ZoneOffset.UTC, now),
        )
        assertNull(nextOccurrence(at, "none", ZoneOffset.UTC, now))
    }
}
