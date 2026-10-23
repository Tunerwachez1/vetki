package dev.vetki.core

import java.time.*

fun nextOccurrence(
    at: Long,
    repeat: String,
    zone: ZoneId = ZoneId.systemDefault(),
    now: Long = System.currentTimeMillis(),
): Long? {
    if (repeat == "none") return null
    var date = Instant.ofEpochMilli(at).atZone(zone)
    do {
        date =
            when (repeat) {
                "daily" -> date.plusDays(1)
                "weekly" -> date.plusWeeks(1)
                else -> return null
            }
    } while (date.toInstant().toEpochMilli() <= now)
    return date.toInstant().toEpochMilli()
}
