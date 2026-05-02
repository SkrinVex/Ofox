package su.SkrinVex.ofox

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class VoicePlayerService : Service() {

    companion object {
        const val CHANNEL_ID = "voice_playback"
        const val NOTIF_ID = 42
        const val ACTION_PLAY_PAUSE = "su.SkrinVex.ofox.PLAY_PAUSE"
        const val ACTION_STOP = "su.SkrinVex.ofox.STOP"

        val state = MutableStateFlow(VoiceState())
    }

    data class VoiceState(
        val isPlaying: Boolean = false,
        val isLoading: Boolean = false,
        val progress: Float = 0f,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val voiceKey: String? = null,
        val senderName: String = "",
        val senderAvatarUrl: String = ""
    )

    inner class LocalBinder : Binder() {
        fun getService() = this@VoicePlayerService
    }

    private val binder = LocalBinder()
    private var player: MediaPlayer? = null
    private lateinit var audioManager: AudioManager
    private lateinit var mediaSession: MediaSessionCompat
    private var focusRequest: AudioFocusRequest? = null
    private var wasPlayingBeforeFocusLoss = false
    private val scope = CoroutineScope(Dispatchers.Main)
    private var progressJob: Job? = null
    private var avatarBitmap: Bitmap? = null

    private val focusListener = AudioManager.OnAudioFocusChangeListener { focus ->
        when (focus) {
            AudioManager.AUDIOFOCUS_LOSS -> { pause(); wasPlayingBeforeFocusLoss = false }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> { pause(); wasPlayingBeforeFocusLoss = true }
            AudioManager.AUDIOFOCUS_GAIN -> if (wasPlayingBeforeFocusLoss) { resume(); wasPlayingBeforeFocusLoss = false }
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        createNotificationChannel()
        mediaSession = MediaSessionCompat(this, "VoicePlayer").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = resume()
                override fun onPause() = pause()
                override fun onSeekTo(pos: Long) = seekTo(pos)
                override fun onStop() { stopPlayback(); stopSelf() }
            })
            isActive = true
        }
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> if (state.value.isPlaying) pause() else resume()
            ACTION_STOP -> { stopPlayback(); stopSelf() }
        }
        return START_NOT_STICKY
    }

    fun load(url: String, voiceKey: String, durationMs: Long, senderName: String, senderAvatarUrl: String = "", startPosition: Long = 0) {
        stopPlayback()
        state.value = VoiceState(isLoading = true, durationMs = durationMs, voiceKey = voiceKey,
            senderName = senderName, senderAvatarUrl = senderAvatarUrl)

        if (!requestAudioFocus()) return

        // Загружаем аватарку асинхронно
        scope.launch(Dispatchers.IO) {
            if (senderAvatarUrl.isNotBlank()) {
                try {
                    val req = ImageRequest.Builder(this@VoicePlayerService)
                        .data(senderAvatarUrl).size(128, 128).build()
                    val result = this@VoicePlayerService.imageLoader.execute(req)
                    if (result is SuccessResult) {
                        avatarBitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    }
                } catch (_: Exception) {}
            }
        }

        val mp = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build())
            setDataSource(url)
            setOnPreparedListener { mp ->
                if (startPosition > 0) mp.seekTo(startPosition.toInt())
                mp.start()
                val dur = mp.duration.toLong()
                state.value = state.value.copy(isLoading = false, isPlaying = true, durationMs = dur)
                updateMediaSession()
                startProgressUpdates()
                startForeground(NOTIF_ID, buildNotification())
            }
            setOnCompletionListener {
                state.value = state.value.copy(isPlaying = false, progress = 0f, positionMs = 0L)
                updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
                updateNotification()
                abandonAudioFocus()
            }
            setOnErrorListener { _, _, _ ->
                state.value = state.value.copy(isLoading = false, isPlaying = false)
                true
            }
            prepareAsync()
        }
        player = mp
        startForeground(NOTIF_ID, buildNotification())
    }

    fun pause() {
        player?.pause()
        state.value = state.value.copy(isPlaying = false)
        updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
        updateNotification()
    }

    fun resume() {
        player?.start()
        state.value = state.value.copy(isPlaying = true)
        startProgressUpdates()
        updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
        updateNotification()
    }

    fun seekTo(posMs: Long) {
        player?.seekTo(posMs.toInt())
        val dur = state.value.durationMs.coerceAtLeast(1)
        state.value = state.value.copy(positionMs = posMs, progress = posMs.toFloat() / dur)
        updateNotification()
    }

    fun stopPlayback() {
        progressJob?.cancel()
        progressJob = null
        player?.release()
        player = null
        avatarBitmap = null
        state.value = VoiceState()
        abandonAudioFocus()
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (true) {
                val p = player ?: break
                try {
                    if (p.isPlaying) {
                        val pos = p.currentPosition.toLong()
                        val dur = p.duration.toLong().coerceAtLeast(1)
                        state.value = state.value.copy(positionMs = pos, progress = pos.toFloat() / dur)
                        updatePlaybackState(PlaybackStateCompat.STATE_PLAYING, pos)
                        updateNotification()
                    }
                } catch (_: IllegalStateException) { break }
                delay(500)
            }
        }
    }

    private fun updateMediaSession() {
        mediaSession.setMetadata(MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, state.value.senderName.ifBlank { "Голосовое" })
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Ofox")
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, state.value.durationMs)
            .apply { avatarBitmap?.let { putBitmap(MediaMetadataCompat.METADATA_KEY_ART, it) } }
            .build())
    }

    private fun updatePlaybackState(pbState: Int, posMs: Long = state.value.positionMs) {
        mediaSession.setPlaybackState(PlaybackStateCompat.Builder()
            .setState(pbState, posMs, 1f)
            .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_SEEK_TO or PlaybackStateCompat.ACTION_STOP)
            .build())
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val isPlaying = state.value.isPlaying
        val sender = state.value.senderName.ifBlank { "Голосовое сообщение" }
        val posMs = state.value.positionMs
        val durMs = state.value.durationMs.coerceAtLeast(1)
        val progressPct = ((posMs.toFloat() / durMs) * 100).toInt()

        fun pi(action: String) = PendingIntent.getService(
            this, action.hashCode(),
            Intent(this, VoicePlayerService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val posStr = formatMs(posMs)
        val durStr = formatMs(durMs)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(sender)
            .setContentText("$posStr / $durStr")
            .setContentIntent(openIntent)
            .setOngoing(isPlaying)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .apply { avatarBitmap?.let { setLargeIcon(it) } }
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Пауза" else "Играть",
                pi(ACTION_PLAY_PAUSE)
            )
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Стоп", pi(ACTION_STOP))
            .setProgress(100, progressPct, false)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession.sessionToken)
                .setShowActionsInCompactView(0, 1))
            .build()
    }

    private fun formatMs(ms: Long): String {
        val s = ms / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }

    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build())
                .setOnAudioFocusChangeListener(focusListener)
                .setWillPauseWhenDucked(true)
                .build()
            focusRequest = req
            audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusListener)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Воспроизведение голосовых", NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                enableVibration(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    override fun onDestroy() {
        stopPlayback()
        mediaSession.release()
        super.onDestroy()
    }
}
