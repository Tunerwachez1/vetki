package dev.vetki.feature

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.vetki.data.Reminder
import dev.vetki.ui.*
import java.time.*
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReminderSheet(
    initial: Reminder?,
    cardId: String?,
    boardId: String?,
    defaultTitle: String,
    initialDate: LocalDate,
    exactAvailable: () -> Boolean,
    onExactAccess: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (Reminder) -> Unit,
    onDelete: ((Reminder) -> Unit)? = null,
) {
    val context = LocalContext.current
    val initialTime = remember {
        initial?.let {
            Instant.ofEpochMilli(it.at).atZone(ZoneId.systemDefault()).toLocalDateTime()
        }
            ?: if (initialDate == LocalDate.now())
                LocalDateTime.now().plusHours(1).withSecond(0).withNano(0)
            else initialDate.atTime(9, 0)
    }
    var title by rememberSaveable { mutableStateOf(initial?.title ?: defaultTitle) }
    var date by
        rememberSaveable(
            stateSaver =
                Saver<LocalDate, String>(
                    save = { it.toString() },
                    restore = { LocalDate.parse(it) },
                )
        ) {
            mutableStateOf(initialTime.toLocalDate())
        }
    var time by
        rememberSaveable(
            stateSaver =
                Saver<LocalTime, String>(
                    save = { it.toString() },
                    restore = { LocalTime.parse(it) },
                )
        ) {
            mutableStateOf(initialTime.toLocalTime())
        }
    var alarm by rememberSaveable { mutableStateOf(initial?.alarm ?: false) }
    var nag by rememberSaveable { mutableStateOf(initial?.nag ?: false) }
    var repeat by rememberSaveable { mutableStateOf(initial?.repeat ?: "none") }
    var error by remember { mutableStateOf<String?>(null) }
    var hasExact by remember { mutableStateOf(exactAvailable()) }
    var hasNotifications by remember { mutableStateOf(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasExact = exactAvailable()
        hasNotifications =
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
    }
    ModalBottomSheet(
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        onDismissRequest = onDismiss,
        containerColor = Background,
    ) {
        Column(
            Modifier.imePadding().fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp)
        ) {
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Напоминание", style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(
                    title,
                    { title = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("О чём напомнить?") },
                    shape = RoundedCornerShape(14.dp),
                )
                Surface(
                    onClick = {
                        DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    date = LocalDate.of(y, m + 1, d)
                                    error = null
                                },
                                date.year,
                                date.monthValue - 1,
                                date.dayOfMonth,
                            )
                            .show()
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = Panel,
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.CalendarMonth, null, tint = Teal)
                        Text(
                            date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Ru)),
                            Modifier.weight(1f).padding(start = 14.dp),
                        )
                        Icon(Icons.Outlined.ChevronRight, null, tint = Muted)
                    }
                }
                Surface(
                    onClick = {
                        TimePickerDialog(
                                context,
                                { _, h, m ->
                                    time = LocalTime.of(h, m)
                                    error = null
                                },
                                time.hour,
                                time.minute,
                                true,
                            )
                            .show()
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = Panel,
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Schedule, null, tint = Teal)
                        Text(
                            time.format(DateTimeFormatter.ofPattern("HH:mm")),
                            Modifier.weight(1f).padding(start = 14.dp),
                        )
                        Icon(Icons.Outlined.ChevronRight, null, tint = Muted)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = {
                            val d = LocalDateTime.now().plusMinutes(1)
                            date = d.toLocalDate()
                            time = d.toLocalTime()
                            error = null
                        },
                        label = { Text("Через 1 мин") },
                    )
                    AssistChip(
                        onClick = {
                            val d = LocalDateTime.now().plusMinutes(10)
                            date = d.toLocalDate()
                            time = d.toLocalTime()
                            error = null
                        },
                        label = { Text("Через 10 мин") },
                    )
                }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        !alarm,
                        { alarm = false },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) {
                        Text("Уведомление")
                    }
                    SegmentedButton(
                        alarm,
                        { alarm = true },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) {
                        Text("Будильник")
                    }
                }
                Text(
                    "Мелодия: Спокойная музыка. В режиме будильника играет по кругу, громкость нарастает за 60 секунд.",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (!alarm)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Повторять каждые 10 минут")
                            Text(
                                "До подтверждения выполнения",
                                color = Muted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Switch(nag, { nag = it })
                    }
                Text("Повторение задачи", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                            "none" to "Один раз",
                            "daily" to "Каждый день",
                            "weekly" to "Каждую неделю",
                        )
                        .forEach { (key, name) ->
                            FilterChip(repeat == key, { repeat = key }, label = { Text(name) })
                        }
                }
                if (!hasExact)
                    Surface(shape = RoundedCornerShape(14.dp), color = Panel) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                "Точные сигналы отключены",
                                color = Amber,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                "Android может задержать напоминание. Разреши точные сигналы в настройках.",
                                color = Muted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            TextButton(onClick = onExactAccess) { Text("Открыть настройки") }
                        }
                    }
                if (!hasNotifications)
                    Text(
                        "При сохранении приложение запросит разрешение на уведомления.",
                        color = Amber,
                        style = MaterialTheme.typography.bodySmall,
                    )
            }
            Spacer(Modifier.height(12.dp))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    val at =
                        date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    if (at <= System.currentTimeMillis()) error = "Выбери время в будущем"
                    else if (title.isBlank()) error = "Добавь название"
                    else {
                        onSave(
                            (initial ?: Reminder(title = title, at = at)).copy(
                                title = title.trim(),
                                cardId = cardId,
                                boardId = boardId,
                                at = at,
                                alarm = alarm,
                                nag = if (alarm) false else nag,
                                repeat = repeat,
                                done = false,
                                delivered = false,
                            )
                        )
                        onDismiss()
                    }
                },
                Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("Сохранить")
            }
            if (initial != null && onDelete != null)
                TextButton(
                    onClick = {
                        onDelete(initial)
                        onDismiss()
                    },
                    Modifier.fillMaxWidth(),
                ) {
                    Text("Удалить напоминание", color = MaterialTheme.colorScheme.error)
                }
        }
    }
}
