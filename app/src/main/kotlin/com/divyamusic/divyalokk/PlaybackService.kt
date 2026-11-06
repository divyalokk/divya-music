package com.divyamusic.divyalokk

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat


// ----------------------------------------------------
// PLAYBACK SERVICE (notification controls)
// ----------------------------------------------------

/**
 * Foreground service that shows the song in the notification bar with previous, play/pause
 * and next buttons, plus a seek bar through a media-style notification and [MediaSession].
 * Playback itself stays in [Playback].
 */
class PlaybackService : Service() {

    private val handler = Handler(Looper.getMainLooper())

    private var session: MediaSession? = null

    private val ticker = object : Runnable {
        override fun run() {
            Playback.tick()
            refresh()
            handler.postDelayed(this, 1000L)
        }
    }

    private val sessionCallback = object : MediaSession.Callback() {
        override fun onPlay() {
            if (!Playback.isPlaying) Playback.playPause()
        }

        override fun onPause() {
            if (Playback.isPlaying) Playback.playPause()
        }

        override fun onSkipToNext() {
            Playback.next()
        }

        override fun onSkipToPrevious() {
            Playback.previous()
        }

        override fun onSeekTo(pos: Long) {
            Playback.seekTo(pos.toInt())
        }
    }

    override fun onCreate() {
        super.onCreate()

        // The service can start on its own (notification buttons), so make sure state is loaded
        Playback.init(this)

        createChannel()

        session = MediaSession(this, "DivyaMusicPlayback").apply {
            @Suppress("DEPRECATION")
            setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS)
            setCallback(sessionCallback)
            isActive = true
        }

        Playback.onStateChanged = { refresh() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        when (intent?.action) {
            ACTION_PLAY_PAUSE -> Playback.playPause()
            ACTION_NEXT -> Playback.next()
            ACTION_PREVIOUS -> Playback.previous()
        }

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )

        handler.removeCallbacks(ticker)
        handler.post(ticker)

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        Playback.onStateChanged = null

        session?.apply {
            isActive = false
            release()
        }
        session = null

        super.onDestroy()
    }

    private fun refresh() {

        // Stop the service once nothing is loaded or playing
        if (Playback.currentIndex < 0 && !Playback.isPlaying) {
            stopSelf()
            return
        }

        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification())
    }

    /** Keeps the media session's metadata and playback state in step with [Playback]. */
    private fun updateSession(song: Song?) {

        val mediaSession = session ?: return

        val max = Playback.duration.coerceAtLeast(1)
        val position = Playback.currentPosition.coerceIn(0, max)

        mediaSession.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, song?.title ?: "DivyaMusic")
                .putString(MediaMetadata.METADATA_KEY_ARTIST, song?.artist ?: "")
                .putLong(MediaMetadata.METADATA_KEY_DURATION, max.toLong())
                .build()
        )

        val actions = PlaybackState.ACTION_PLAY or
            PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_SKIP_TO_NEXT or
            PlaybackState.ACTION_SKIP_TO_PREVIOUS or
            PlaybackState.ACTION_SEEK_TO

        mediaSession.setPlaybackState(
            PlaybackState.Builder()
                .setActions(actions)
                .setState(
                    if (Playback.isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    position.toLong(),
                    if (Playback.isPlaying) 1f else 0f
                )
                .build()
        )
    }

    @Suppress("DEPRECATION")
    private fun buildNotification(): Notification {

        val song = Playback.songs.getOrNull(Playback.currentIndex)

        updateSession(song)

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Progress bar: the notification shows how far the song has played
        val max = Playback.duration.coerceAtLeast(1)
        val position = Playback.currentPosition.coerceIn(0, max)

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }

        builder
            .setSmallIcon(R.drawable.music)
            .setContentTitle(song?.title ?: "DivyaMusic")
            .setContentText(song?.artist ?: "Nothing playing")
            .setContentIntent(openApp)
            .setOnlyAlertOnce(true)
            .setOngoing(Playback.isPlaying)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setPriority(Notification.PRIORITY_LOW)
            .setProgress(max, position, false)
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(session?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )

        builder.addAction(
            R.drawable.skip_previous,
            "Previous",
            actionIntent(ACTION_PREVIOUS, 1)
        )
        builder.addAction(
            if (Playback.isPlaying) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play,
            if (Playback.isPlaying) "Pause" else "Play",
            actionIntent(ACTION_PLAY_PAUSE, 2)
        )
        builder.addAction(
            R.drawable.skip_next,
            "Next",
            actionIntent(ACTION_NEXT, 3)
        )

        return builder.build()
    }

    private fun actionIntent(action: String, code: Int): PendingIntent {

        val intent = Intent(this, PlaybackService::class.java).apply { this.action = action }

        return PendingIntent.getService(
            this,
            code,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createChannel() {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Song controls in the notification bar"
            setShowBadge(false)
        }

        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {

        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_PLAY_PAUSE = "com.divyamusic.divyalokk.PLAY_PAUSE"
        const val ACTION_NEXT = "com.divyamusic.divyalokk.NEXT"
        const val ACTION_PREVIOUS = "com.divyamusic.divyalokk.PREVIOUS"

        /** Starts the service in the foreground when playback begins. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, PlaybackService::class.java)
            )
        }
    }
}
