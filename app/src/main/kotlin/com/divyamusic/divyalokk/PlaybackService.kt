package com.divyamusic.divyalokk

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat


// ----------------------------------------------------
// PLAYBACK SERVICE (notification controls)
// ----------------------------------------------------

/**
 * Foreground service that shows the song in the notification bar with previous, play/pause
 * and next buttons. Playback itself stays in [Playback].
 */
class PlaybackService : Service() {

    private val handler = Handler(Looper.getMainLooper())

    private val ticker = object : Runnable {
        override fun run() {
            Playback.tick()
            refresh()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()

        // The service can start on its own (notification buttons), so make sure state is loaded
        Playback.init(this)

        createChannel()

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

    private fun buildNotification(): Notification {

        val song = Playback.songs.getOrNull(Playback.currentIndex)

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

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.music)
            .setContentTitle(song?.title ?: "DivyaMusic")
            .setContentText(song?.artist ?: "Nothing playing")
            .setContentIntent(openApp)
            .setOnlyAlertOnce(true)
            .setOngoing(Playback.isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setProgress(max, position, false)
            .addAction(
                R.drawable.skip_previous,
                "Previous",
                actionIntent(ACTION_PREVIOUS, 1)
            )
            .addAction(
                if (Playback.isPlaying) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_media_play,
                if (Playback.isPlaying) "Pause" else "Play",
                actionIntent(ACTION_PLAY_PAUSE, 2)
            )
            .addAction(
                R.drawable.skip_next,
                "Next",
                actionIntent(ACTION_NEXT, 3)
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun actionIntent(action: String, code: Int): PendingIntent {

        val intent = Intent(this, PlaybackService::class.java).setAction(action)

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
