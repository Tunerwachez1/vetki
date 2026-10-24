package dev.vetki.feature

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.vetki.i18n.R as StringsRes
import dev.vetki.i18n.tr
import dev.vetki.reminders.*
import dev.vetki.ui.*
import kotlin.math.roundToInt

@Composable
fun RingingScreen(alarm: RingingAlarm) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = {},
        properties =
            DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
            ),
    ) {
        Surface(Modifier.fillMaxSize(), color = Background) {
            Column(
                Modifier.fillMaxSize().safeDrawingPadding().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(
                        Icons.Outlined.Alarm,
                        null,
                        Modifier.padding(32.dp).size(64.dp),
                        tint = Teal,
                    )
                }
                Spacer(Modifier.height(32.dp))
                Text(
                    tr(StringsRes.string.alarm_2),
                    color = Teal,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    alarm.title,
                    Modifier.padding(vertical = 20.dp),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
                if (alarm.ids.size > 1)
                    Text(tr(StringsRes.string.more_reminders, alarm.ids.size - 1), color = Muted)
                Text(
                    alarm.error
                        ?: if (alarm.playing) tr(StringsRes.string.calm_music_is_playing_on_repeat)
                        else tr(StringsRes.string.waiting_for_audio_access),
                    color = Muted,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                LinearProgressIndicator(
                    progress = { alarm.gain },
                    modifier = Modifier.fillMaxWidth().height(5.dp),
                    color = Teal,
                    trackColor = Panel,
                )
                Text(
                    tr(StringsRes.string.sound_volume, (alarm.gain * 100).roundToInt()),
                    Modifier.padding(top = 12.dp),
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    tr(StringsRes.string.gradually_increases_over_60_seconds),
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(36.dp))
                Button(
                    onClick = { AlarmPlaybackService.control(context, false) },
                    Modifier.fillMaxWidth().height(60.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(tr(StringsRes.string.stop))
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { AlarmPlaybackService.control(context, true) },
                    Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(tr(StringsRes.string.snooze_for_10_minutes))
                }
            }
        }
    }
}
