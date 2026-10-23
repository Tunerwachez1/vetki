package dev.vetki.core

/** Player gain relative to Android's configured alarm volume. */
fun alarmGain(elapsedMillis: Long): Float =
    .12f + .88f * (elapsedMillis.coerceIn(0, 60_000).toFloat() / 60_000f)
