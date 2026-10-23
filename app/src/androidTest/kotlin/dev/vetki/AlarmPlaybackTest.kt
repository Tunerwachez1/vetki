package dev.vetki

import android.content.Intent
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.vetki.data.*
import dev.vetki.reminders.*
import java.io.File
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real system alarm -> background service -> looping player -> stop action. */
@RunWith(AndroidJUnit4::class)
class AlarmPlaybackTest {
    @Test
    fun backgroundAlarmLoopsRampsAndStops() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val dao = VetkiDatabase.get(context).dao()
        val scheduler = ReminderScheduler(context)
        val reminder =
            Reminder(
                title = "Проверка будильника",
                at = System.currentTimeMillis() + 4000,
                alarm = true,
            )
        suspend fun await(condition: () -> Boolean) {
            withTimeout(15000) { while (!condition()) delay(100) }
        }
        try {
            val launch =
                context.packageManager
                    .getLaunchIntentForPackage(context.packageName)!!
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launch)
            delay(1000)
            assertTrue(
                "Exact alarms must be enabled on the test device",
                scheduler.exactAvailable(),
            )
            dao.save(reminder)
            scheduler.schedule(reminder)
            instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME").close()
            await {
                AlarmPlaybackState.current.value?.ids?.contains(reminder.id) == true &&
                    AlarmPlaybackState.current.value?.playing == true
            }
            val gain = AlarmPlaybackState.current.value!!.gain
            delay(
                12000
            ) // The supplied file lasts ~10 s: continuing playback crosses a loop boundary.
            val later = AlarmPlaybackState.current.value!!
            assertTrue(later.playing)
            assertTrue("Gain must rise while the melody loops", later.gain > gain)
            assertTrue(dao.reminder(reminder.id)!!.delivered)
            context.startActivity(launch)
            delay(1200)
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                File(context.cacheDir, "ringing-verified.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
            AlarmPlaybackService.control(context, false)
            await { AlarmPlaybackState.current.value == null }
            withTimeout(5000) { while (dao.reminder(reminder.id)?.done != true) delay(100) }
            assertTrue(dao.reminder(reminder.id)!!.done)
        } finally {
            scheduler.cancel(reminder.id)
            delay(200)
            dao.deleteReminder(reminder.id)
        }
    }
}
