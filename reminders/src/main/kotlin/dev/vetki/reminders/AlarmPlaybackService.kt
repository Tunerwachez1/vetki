package dev.vetki.reminders

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.media.*
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.vetki.core.alarmGain
import dev.vetki.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** One player can serve several simultaneous alarms; each alarm remains dismissible. */
data class RingingAlarm(
    val ids: List<String>,
    val title: String,
    val gain: Float,
    val playing: Boolean,
    val error: String? = null,
)

object AlarmPlaybackState {
    internal val mutable = MutableStateFlow<RingingAlarm?>(null)
    val current: StateFlow<RingingAlarm?> = mutable.asStateFlow()
}

class AlarmPlaybackService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val queue = linkedMapOf<String, Reminder>()
    private var player: MediaPlayer? = null
    private var prepared = false
    private var startedAt = 0L
    private var ramp: Job? = null
    private lateinit var audio: AudioManager
    private lateinit var focus: AudioFocusRequest
    private val attributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    private val vibrator by lazy { getSystemService(Vibrator::class.java) }

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        audio = getSystemService(AudioManager::class.java)
        focus =
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(
                    { change ->
                        when (change) {
                            AudioManager.AUDIOFOCUS_GAIN -> if (prepared) play()
                            AudioManager.AUDIOFOCUS_LOSS,
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                                player?.takeIf { it.isPlaying }?.pause()
                                AlarmPlaybackState.mutable.value =
                                    AlarmPlaybackState.current.value?.copy(playing = false)
                            }
                        }
                    },
                    Handler(Looper.getMainLooper()),
                )
                .build()
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(
                NotificationChannel(
                        CHANNEL,
                        "Звонящий будильник",
                        NotificationManager.IMPORTANCE_HIGH,
                    )
                    .apply {
                        description =
                            "Мелодия играет по кругу до остановки, громкость плавно нарастает"
                        setSound(null, null)
                        enableVibration(false)
                    }
            )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == REMOVE) {
            val id = intent.getStringExtra("id")
            queue.remove(id)
            persist()
            if (queue.isEmpty()) finishRinging()
            else {
                updateState()
                updateNotification()
            }
            return START_NOT_STICKY
        }
        val id = intent?.getStringExtra("id")
        val ids = if (id != null) setOf(id) else activeIds(this)
        if (ids.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }
        val title = intent?.getStringExtra("title") ?: "Будильник"
        ServiceCompat.startForeground(
            this,
            NOTIFICATION,
            notification(title),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        scope.launch {
            val dao = VetkiDatabase.get(this@AlarmPlaybackService).dao()
            ids.forEach { key -> dao.reminder(key)?.takeIf { !it.done }?.let { queue[key] = it } }
            if (queue.isEmpty()) {
                finishRinging()
                return@launch
            }
            persist()
            updateState()
            updateNotification()
            if (player == null) prepareMelody()
        }
        return START_STICKY
    }

    private fun persist() {
        getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit()
            .putStringSet("active", queue.keys.toSet())
            .apply()
    }

    private fun prepareMelody() {
        try {
            player =
                MediaPlayer().also { media ->
                    media.setAudioAttributes(attributes)
                    media.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK)
                    resources.openRawResourceFd(R.raw.calm_reminder).use { fd ->
                        media.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                    }
                    media.isLooping = true
                    media.setVolume(.12f, .12f)
                    media.setOnErrorListener { _, _, _ ->
                        playbackError()
                        true
                    }
                    media.setOnPreparedListener {
                        prepared = true
                        when (audio.requestAudioFocus(focus)) {
                            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> play()
                            AudioManager.AUDIOFOCUS_REQUEST_FAILED ->
                                AlarmPlaybackState.mutable.value =
                                    AlarmPlaybackState.current.value?.copy(
                                        error = "Android не предоставил доступ к звуку"
                                    )
                        }
                    }
                    media.prepareAsync()
                }
            vibrator?.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 350, 900, 350, 1800), 0)
            )
        } catch (_: Exception) {
            playbackError()
        }
    }

    private fun play() {
        val media = player ?: return
        if (!prepared) return
        if (startedAt == 0L) startedAt = SystemClock.elapsedRealtime()
        if (!media.isPlaying) media.start()
        android.util.Log.i("VetkiAlarm", "Looping playback started")
        ramp?.cancel()
        ramp =
            scope.launch {
                while (isActive) {
                    val gain = alarmGain(SystemClock.elapsedRealtime() - startedAt)
                    media.setVolume(gain, gain)
                    if ((SystemClock.elapsedRealtime() - startedAt) / 1000 % 10L == 0L)
                        android.util.Log.d(
                            "VetkiAlarm",
                            "gain=$gain; looping=${media.isLooping}; playing=${media.isPlaying}",
                        )
                    updateState(gain)
                    delay(1000)
                }
            }
    }

    private fun updateState(gain: Float = AlarmPlaybackState.current.value?.gain ?: .12f) {
        queue.values.firstOrNull()?.let { first ->
            AlarmPlaybackState.mutable.value =
                RingingAlarm(
                    queue.keys.toList(),
                    first.title,
                    gain,
                    player?.let { prepared && it.isPlaying } ?: false,
                )
        }
    }

    private fun playbackError() {
        ramp?.cancel()
        ramp = null
        player?.release()
        player = null
        prepared = false
        AlarmPlaybackState.mutable.value =
            AlarmPlaybackState.current.value?.copy(
                playing = false,
                error = "Не удалось воспроизвести мелодию",
            )
    }

    private fun notification(title: String): Notification {
        val launch =
            packageManager
                .getLaunchIntentForPackage(packageName)!!
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val open =
            PendingIntent.getActivity(
                this,
                NOTIFICATION,
                launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        fun action(name: String) =
            PendingIntent.getBroadcast(
                this,
                name.hashCode(),
                Intent(this, ReminderReceiver::class.java).setAction(name),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(if (queue.size > 1) "$title · ещё ${queue.size-1}" else title)
            .setContentText("Спокойная музыка · звонит до остановки")
            .setContentIntent(open)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, "Остановить", action("stop_all"))
            .addAction(0, "Через 10 мин", action("snooze_all"))
            .build()
    }

    private fun updateNotification() {
        queue.values.firstOrNull()?.let {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION, notification(it.title))
        }
    }

    private fun finishRinging() {
        android.util.Log.i("VetkiAlarm", "Playback stopped by dismissal")
        queue.clear()
        persist()
        releasePlayer()
        AlarmPlaybackState.mutable.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releasePlayer() {
        ramp?.cancel()
        ramp = null
        player?.release()
        player = null
        prepared = false
        vibrator?.cancel()
        if (::audio.isInitialized && ::focus.isInitialized) audio.abandonAudioFocusRequest(focus)
    }

    override fun onDestroy() {
        releasePlayer()
        AlarmPlaybackState.mutable.value = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "ringing_v1"
        private const val PREFS = "ringing"
        private const val NOTIFICATION = 73410
        private const val REMOVE = "remove"

        fun activeIds(context: Context): Set<String> =
            context
                .getSharedPreferences(PREFS, MODE_PRIVATE)
                .getStringSet("active", emptySet())
                .orEmpty()
                .toSet()

        fun start(context: Context, reminder: Reminder) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AlarmPlaybackService::class.java)
                    .setAction("ring")
                    .putExtra("id", reminder.id)
                    .putExtra("title", reminder.title),
            )
        }

        fun stop(context: Context, id: String) {
            if (AlarmPlaybackState.current.value?.ids?.contains(id) == true)
                context.startService(
                    Intent(context, AlarmPlaybackService::class.java)
                        .setAction(REMOVE)
                        .putExtra("id", id)
                )
            else {
                val prefs = context.getSharedPreferences(PREFS, MODE_PRIVATE)
                prefs.edit().putStringSet("active", activeIds(context) - id).apply()
            }
        }

        fun control(context: Context, snooze: Boolean) {
            context.sendBroadcast(
                Intent(context, ReminderReceiver::class.java)
                    .setAction(if (snooze) "snooze_all" else "stop_all")
            )
        }
    }
}
