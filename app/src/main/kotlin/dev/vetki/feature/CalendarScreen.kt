package dev.vetki.feature

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.vetki.data.*
import dev.vetki.i18n.R as StringsRes
import dev.vetki.i18n.tr
import dev.vetki.ui.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable
fun ReminderRow(r: Reminder, boards: List<Board>, onOpen: () -> Unit, onComplete: () -> Unit) {
    Surface(
        onClick = onOpen,
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Panel,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.width(3.dp)
                    .height(44.dp)
                    .background(
                        if (r.done) Muted
                        else if (r.at < System.currentTimeMillis()) Amber else Teal,
                        RoundedCornerShape(2.dp),
                    )
            )
            Column(Modifier.width(58.dp).padding(start = 10.dp)) {
                Text(
                    Instant.ofEpochMilli(r.at)
                        .atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("HH:mm")),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (r.alarm) Icon(Icons.Outlined.Alarm, null, Modifier.size(14.dp), tint = Amber)
            }
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(r.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    if (r.done) tr(StringsRes.string.completed)
                    else
                        r.boardId?.let { id -> boards.firstOrNull { it.id == id }?.title }
                            ?: if (r.nag) tr(StringsRes.string.repeat_until_completed)
                            else tr(StringsRes.string.reminder),
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onComplete, enabled = !r.done) {
                Icon(
                    if (r.done) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    tr(StringsRes.string.complete, r.title),
                    tint = if (r.done) Teal else Muted,
                )
            }
        }
    }
}

@Composable
fun CalendarScreen(
    reminders: List<Reminder>,
    boards: List<Board>,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    onOpen: (Reminder) -> Unit,
    onComplete: (Reminder) -> Unit,
    modifier: Modifier = Modifier,
) {
    var month by remember { mutableStateOf(YearMonth.from(selected)) }
    var mode by remember { mutableIntStateOf(0) }
    LaunchedEffect(selected) { month = YearMonth.from(selected) }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 0.dp, 20.dp, 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        if (mode == 0) month = month.minusMonths(1)
                        else onSelect(selected.minusDays(if (mode == 1) 7 else 1))
                    }
                ) {
                    Icon(Icons.Outlined.ChevronLeft, tr(StringsRes.string.previous_period))
                }
                Text(
                    month
                        .atDay(1)
                        .format(DateTimeFormatter.ofPattern("LLLL yyyy", AppLocale))
                        .replaceFirstChar { it.titlecase(AppLocale) },
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                IconButton(
                    onClick = {
                        if (mode == 0) month = month.plusMonths(1)
                        else onSelect(selected.plusDays(if (mode == 1) 7 else 1))
                    }
                ) {
                    Icon(Icons.Outlined.ChevronRight, tr(StringsRes.string.next_period))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                        tr(StringsRes.string.month),
                        tr(StringsRes.string.week),
                        tr(StringsRes.string.day),
                    )
                    .forEachIndexed { i, label ->
                        FilterChip(
                            mode == i,
                            { mode = i },
                            label = { Text(label) },
                            modifier = Modifier.weight(1f),
                        )
                    }
            }
            if (mode < 2) {
                Row(Modifier.padding(top = 12.dp)) {
                    listOf(
                            tr(StringsRes.string.mon),
                            tr(StringsRes.string.tue),
                            tr(StringsRes.string.wed),
                            tr(StringsRes.string.thu),
                            tr(StringsRes.string.fri),
                            tr(StringsRes.string.sat),
                            tr(StringsRes.string.sun),
                        )
                        .forEach {
                            Text(
                                it,
                                Modifier.weight(1f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                color = Muted,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                }
                val first =
                    if (mode == 0)
                        month.atDay(1).minusDays((month.atDay(1).dayOfWeek.value - 1).toLong())
                    else selected.minusDays((selected.dayOfWeek.value - 1).toLong())
                val weeks =
                    if (mode == 0)
                        ((month.lengthOfMonth() + month.atDay(1).dayOfWeek.value - 2) / 7 + 1)
                    else 1
                repeat(weeks) { week ->
                    Row(Modifier.fillMaxWidth()) {
                        repeat(7) { column ->
                            val date = first.plusDays((week * 7 + column).toLong())
                            val count = reminders.count { !it.done && dayOf(it.at) == date }
                            Column(
                                Modifier.weight(1f).height(52.dp).clickable { onSelect(date) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Box(
                                    Modifier.size(32.dp)
                                        .background(
                                            if (date == selected) Teal
                                            else if (date == LocalDate.now())
                                                MaterialTheme.colorScheme.primaryContainer
                                            else androidx.compose.ui.graphics.Color.Transparent,
                                            CircleShape,
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "${date.dayOfMonth}",
                                        color =
                                            if (date == selected) Background
                                            else if (YearMonth.from(date) != month)
                                                Muted.copy(alpha = .45f)
                                            else MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                                if (count > 0)
                                    Box(Modifier.size(4.dp).background(Amber, CircleShape))
                                else Spacer(Modifier.height(4.dp))
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    selected.format(DateTimeFormatter.ofPattern("d MMMM, EEEE", AppLocale)),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(
                    onClick = {
                        onSelect(LocalDate.now())
                        month = YearMonth.now()
                    }
                ) {
                    Text(tr(StringsRes.string.today))
                }
            }
        }
        val selectedReminders = reminders.filter { dayOf(it.at) == selected }.sortedBy { it.at }
        if (selectedReminders.isEmpty())
            item {
                EmptyState(
                    Icons.Outlined.EventAvailable,
                    tr(StringsRes.string.a_free_day),
                    tr(StringsRes.string.add_a_task_or_reminder_with_the_button),
                )
            }
        items(selectedReminders, key = { it.id }) { r ->
            ReminderRow(r, boards, { onOpen(r) }, { onComplete(r) })
        }
    }
}

@Composable
fun TodayScreen(
    reminders: List<Reminder>,
    boards: List<Board>,
    onOpen: (Reminder) -> Unit,
    onComplete: (Reminder) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now()
    val active = reminders.filter { !it.done }
    val overdue = active.filter { dayOf(it.at) < today }
    val due = active.filter { dayOf(it.at) == today }
    val upcoming = active.filter { dayOf(it.at) > today }.take(10)
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 0.dp, 20.dp, 100.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Column(Modifier.fillMaxWidth().padding(22.dp)) {
                    Text(
                        today.format(DateTimeFormatter.ofPattern("d MMMM, EEEE", AppLocale)),
                        color = Teal,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        if (due.isEmpty()) tr(StringsRes.string.room_for_new_plans)
                        else tr(StringsRes.string.today_2, due.size),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        tr(StringsRes.string.even_a_small_step_moves_you_forward),
                        color = Muted,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
        if (overdue.isNotEmpty()) {
            item { SectionLabel(tr(StringsRes.string.overdue), "${overdue.size}") }
            items(overdue, key = { "past-${it.id}" }) { r ->
                ReminderRow(r, boards, { onOpen(r) }, { onComplete(r) })
            }
        }
        item { SectionLabel(tr(StringsRes.string.due_today), "${due.size}") }
        if (due.isEmpty())
            item {
                EmptyState(
                    Icons.Outlined.CheckCircle,
                    tr(StringsRes.string.all_clear),
                    tr(StringsRes.string.no_reminders_for_today),
                )
            }
        items(due, key = { it.id }) { r ->
            ReminderRow(r, boards, { onOpen(r) }, { onComplete(r) })
        }
        if (upcoming.isNotEmpty()) {
            item { SectionLabel(tr(StringsRes.string.upcoming)) }
            items(upcoming, key = { "next-${it.id}" }) { r ->
                Column {
                    Text(
                        dateTime(r.at),
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    ReminderRow(r, boards, { onOpen(r) }, { onComplete(r) })
                }
            }
        }
    }
}
