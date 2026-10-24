package dev.vetki.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.vetki.i18n.R as StringsRes
import dev.vetki.i18n.tr

val Teal = Color(0xFF55D9C5)
val Muted = Color(0xFF99ADB5)
val Panel = Color(0xFF1B272D)
val Background = Color(0xFF10171B)
val Amber = Color(0xFFF3BE6B)
val StatusColors = listOf(Teal, Amber, Color(0xFF8BAFF4), Color(0xFFB99AE8))
val StatusNames
    get() =
        listOf(
            tr(StringsRes.string.to_decide),
            tr(StringsRes.string.to_clarify),
            tr(StringsRes.string.almost_ready),
            tr(StringsRes.string.done),
        )
private val scheme =
    darkColorScheme(
        primary = Teal,
        onPrimary = Color(0xFF00382F),
        primaryContainer = Color(0xFF224D46),
        onPrimaryContainer = Color(0xFFE5EDEF),
        secondaryContainer = Color(0xFF25443F),
        onSecondaryContainer = Teal,
        secondary = Amber,
        background = Background,
        surface = Background,
        surfaceVariant = Panel,
        onSurface = Color(0xFFE5EDEF),
        onSurfaceVariant = Muted,
        outline = Color(0xFF3A4A52),
    )

@Composable
fun VetkiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
