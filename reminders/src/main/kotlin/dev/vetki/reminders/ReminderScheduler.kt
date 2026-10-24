package dev.vetki.reminders

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.vetki.core.nextOccurrence
import dev.vetki.data.*
import dev.vetki.i18n.R as StringsRes
import dev.vetki.i18n.tr
import kotlinx.coroutines.*

class ReminderScheduler(private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    fun exactAvailable() = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()

    private fun pending(id: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            id.hashCode(),
            Intent(context, ReminderReceiver::class.java)
                .setAction("fire")
                .setData(android.net.Uri.parse("vetki://reminder/$id"))
                .putExtra("id", id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun schedule(reminder: Reminder, trigger: Long = reminder.at) {
        if (reminder.done) {
            cancel(reminder.id)
            return
        }
        val time = trigger.coerceAtLeast(System.currentTimeMillis() + 1000)
        val pi = pending(reminder.id)
        try {
            if (exactAvailable()) {
                if (reminder.alarm)
                    alarms.setAlarmClock(
                        AlarmManager.AlarmClockInfo(time, openIntent(reminder)),
                        pi,
                    )
                else alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
            } else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
        } catch (_: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
        }
    }

    fun cancel(id: String) {
        AlarmPlaybackService.stop(context, id)
        alarms.cancel(pending(id))
        context.getSystemService(NotificationManager::class.java).cancel(id.hashCode())
    }

    suspend fun restore() {
        val dao = VetkiDatabase.get(context).dao()
        dao.allReminders()
            .filter { !it.done }
            .forEach { r ->
                val trigger =
                    when {
                        !r.delivered -> r.at
                        !r.alarm && r.nag -> r.nextAt ?: System.currentTimeMillis() + 600_000
                        r.repeat != "none" -> r.nextAt ?: nextOccurrence(r.at, r.repeat)
                        else -> null
                    }
                if (trigger != null) {
                    if (r.delivered && r.nextAt != trigger) dao.save(r.copy(nextAt = trigger))
                    schedule(r, trigger)
                }
            }
    }

    private fun openIntent(r: Reminder): PendingIntent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
        intent
            .putExtra("boardId", r.boardId)
            .putExtra("cardId", r.cardId)
            .putExtra("reminderId", r.id)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            r.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun action(r: Reminder, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            (r.id + action).hashCode(),
            Intent(context, ReminderReceiver::class.java)
                .setAction(action)
                .setData(android.net.Uri.parse("vetki://$action/${r.id}"))
                .putExtra("id", r.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    // Use the resource name, not its numeric ID: the URI survives resource renumbering.
    private fun melodyUri(): Uri =
        Uri.Builder()
            .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority(context.packageName)
            .appendPath("raw")
            .appendPath(context.resources.getResourceEntryName(R.raw.calm_reminder))
            .build()

    /** Returns true if a continuous foreground alarm was launched. */
    fun notify(r: Reminder): Boolean {
        if (r.alarm) {
            try {
                AlarmPlaybackService.start(context, r)
                return true
            } catch (_: IllegalStateException) {
                /* Background start denied: retain an actionable notification. */
            } catch (_: SecurityException) {
                /* Permission may have changed since scheduling. */
            }
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        val normal =
            NotificationChannel(
                    "reminders_calm_v1",
                    tr(StringsRes.string.reminders_calm_music),
                    NotificationManager.IMPORTANCE_HIGH,
                )
                .apply {
                    enableVibration(true)
                    setSound(
                        melodyUri(),
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .build(),
                    )
                }
        val alarm =
            NotificationChannel(
                    "alarms_calm_v1",
                    tr(StringsRes.string.alarms_calm_music),
                    NotificationManager.IMPORTANCE_HIGH,
                )
                .apply {
                    enableVibration(true)
                    setSound(
                        melodyUri(),
                        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
                    )
                }
        manager.createNotificationChannels(listOf(normal, alarm))
        manager.deleteNotificationChannel("reminders")
        manager.deleteNotificationChannel("alarms")
        if (
            Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
        )
            return false
        val notification =
            NotificationCompat.Builder(
                    context,
                    if (r.alarm) "alarms_calm_v1" else "reminders_calm_v1",
                )
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(r.title)
                .setContentText(
                    if (r.nag) tr(StringsRes.string.we_will_remind_you_again_in_10_minutes)
                    else tr(StringsRes.string.time_for_your_plans)
                )
                .setContentIntent(openIntent(r))
                .setAutoCancel(true)
                .setCategory(
                    if (r.alarm) NotificationCompat.CATEGORY_ALARM
                    else NotificationCompat.CATEGORY_REMINDER
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .addAction(0, tr(StringsRes.string.completed), action(r, "done"))
                .addAction(0, tr(StringsRes.string.in_10_min), action(r, "snooze"))
                .build()
        manager.notify(r.id.hashCode(), notification)
        return false
    }

    suspend fun snooze(r: Reminder) {
        cancel(r.id)
        val updated =
            r.copy(
                at = System.currentTimeMillis() + 600_000,
                delivered = false,
                done = false,
                nextAt = null,
            )
        VetkiDatabase.get(context).dao().save(updated)
        schedule(updated)
    }

    suspend fun complete(r: Reminder) {
        cancel(r.id)
        val dao = VetkiDatabase.get(context).dao()
        val next = nextOccurrence(r.at, r.repeat)
        if (next != null) {
            val updated = r.copy(at = next, delivered = false, done = false, nextAt = null)
            dao.save(updated)
            schedule(updated)
        } else {
            dao.save(r.copy(done = true))
            r.cardId?.let { dao.card(it)?.let { c -> dao.save(c.copy(done = true)) } }
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val dao = VetkiDatabase.get(context).dao()
                val scheduler = ReminderScheduler(context)
                if (intent.action == "stop_all" || intent.action == "snooze_all") {
                    val ids =
                        AlarmPlaybackService.activeIds(context) +
                            AlarmPlaybackState.current.value?.ids.orEmpty()
                    ids.forEach { id ->
                        dao.reminder(id)?.let {
                            if (intent.action == "snooze_all") scheduler.snooze(it)
                            else scheduler.complete(it)
                        }
                    }
                    return@launch
                }
                val r = dao.reminder(intent.getStringExtra("id") ?: return@launch) ?: return@launch
                when (intent.action) {
                    "done" -> scheduler.complete(r)
                    "snooze" -> scheduler.snooze(r)
                    "fire" ->
                        if (!r.done) {
                            // A repeat keeps the original occurrence until it is delivered or
                            // dismissed.
                            val occurrence =
                                if (r.delivered && (!r.nag || r.alarm) && r.nextAt != null)
                                    r.copy(at = r.nextAt!!, delivered = false, nextAt = null)
                                else r
                            val continuous = scheduler.notify(occurrence)
                            val next =
                                if (!continuous && occurrence.nag)
                                    System.currentTimeMillis() + 600_000
                                else nextOccurrence(occurrence.at, occurrence.repeat)
                            val updated = occurrence.copy(delivered = true, nextAt = next)
                            dao.save(updated)
                            if (next != null) scheduler.schedule(updated, next)
                        }
                }
            } finally {
                result.finish()
            }
        }
    }
}

class RestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                ReminderScheduler(context).restore()
            } finally {
                result.finish()
            }
        }
    }
}
