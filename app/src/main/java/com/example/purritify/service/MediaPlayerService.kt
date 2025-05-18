package com.example.purritify.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media.app.NotificationCompat.MediaStyle
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.example.purritify.MainActivity
import com.example.purritify.R
import com.example.purritify.data.model.PlayableSong

class MediaPlayerService : Service() {
    private val TAG = "MediaPlayerService"

    companion object {
        const val CHANNEL_ID = "PurrytifyMediaChannel"
        const val NOTIFICATION_ID = 1

        // Action constants for notification controls
        const val ACTION_PLAY_PAUSE = "com.example.purritify.PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.purritify.NEXT"
        const val ACTION_PREVIOUS = "com.example.purritify.PREVIOUS"
        const val ACTION_STOP = "com.example.purritify.STOP"

        // Broadcast actions for updating UI
        const val BROADCAST_PLAYBACK_STATE_CHANGED = "com.example.purritify.PLAYBACK_STATE_CHANGED"
        const val BROADCAST_CURRENT_SONG_CHANGED = "com.example.purritify.CURRENT_SONG_CHANGED"

        // Flag to track service state
        var isServiceRunning = false
    }

    private lateinit var mediaSession: MediaSessionCompat
    private var currentSong: PlayableSong? = null
    private var isPlaying = false
    private var artworkBitmap: Bitmap? = null

    private val notificationActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_PLAY_PAUSE -> {
                    // Broadcast to PlayerViewModel
                    val playPauseIntent = Intent(BROADCAST_PLAYBACK_STATE_CHANGED)
                    LocalBroadcastManager.getInstance(this@MediaPlayerService)
                        .sendBroadcast(playPauseIntent)
                }
                ACTION_NEXT -> {
                    val nextIntent = Intent(ACTION_NEXT)
                    LocalBroadcastManager.getInstance(this@MediaPlayerService)
                        .sendBroadcast(nextIntent)
                }
                ACTION_PREVIOUS -> {
                    val prevIntent = Intent(ACTION_PREVIOUS)
                    LocalBroadcastManager.getInstance(this@MediaPlayerService)
                        .sendBroadcast(prevIntent)
                }
                ACTION_STOP -> {
                    stopSelf()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        // Create media session
        mediaSession = MediaSessionCompat(this, "PurrytifyMediaSession")

        // Register receiver for notification actions
        val intentFilter = IntentFilter().apply {
            addAction(ACTION_PLAY_PAUSE)
            addAction(ACTION_NEXT)
            addAction(ACTION_PREVIOUS)
            addAction(ACTION_STOP)
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Context.RECEIVER_NOT_EXPORTED
        } else {
            0
        }

        registerReceiver(notificationActionReceiver, intentFilter, flags)

        createNotificationChannel()
        isServiceRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "UPDATE_SONG" -> {
                val songTitle = intent.getStringExtra("title") ?: ""
                val songArtist = intent.getStringExtra("artist") ?: ""
                val songArtwork = intent.getStringExtra("artwork") ?: ""
                val songId = intent.getIntExtra("id", -1)

                updateCurrentSong(songTitle, songArtist, songArtwork, songId)
            }
            "UPDATE_PLAYBACK_STATE" -> {
                isPlaying = intent.getBooleanExtra("isPlaying", false)
                updateNotification()
            }
        }

        return START_STICKY
    }

    private fun updateCurrentSong(title: String, artist: String, artwork: String, id: Int) {
        // Create a simple PlayableSong object for the notification
        currentSong = object : PlayableSong {
            override val title: String = title
            override val artist: String = artist
            override val pathArtwork: String = artwork
            override val pathFile: String = ""
            override val duration: String = ""
            override val id: Int = id
        }

        // Load artwork for notification
        loadArtwork(artwork)
    }

    private fun loadArtwork(artworkPath: String) {
        try {
            Glide.with(this)
                .asBitmap()
                .load(artworkPath.toUri())
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        artworkBitmap = resource
                        updateNotification()
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {
                        // Use default artwork
                        artworkBitmap = ContextCompat.getDrawable(
                            this@MediaPlayerService,
                            R.drawable.sample_cover
                        )?.toBitmap()
                        updateNotification()
                    }
                })
        } catch (e: Exception) {
            Log.e(TAG, "Error loading artwork: ${e.message}")
            artworkBitmap = ContextCompat.getDrawable(this, R.drawable.sample_cover)?.toBitmap()
            updateNotification()
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Media Playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Used for media playback controls"
            setShowBadge(false)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun updateNotification() {
        if (currentSong == null) return

        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(): Notification {
        // Create intent for when notification is clicked
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("openPlayer", true)
            putExtra("songId", currentSong?.id ?: -1)
        }

        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create action intents
        val playPauseIntent = Intent(ACTION_PLAY_PAUSE)
        val playPausePendingIntent = PendingIntent.getBroadcast(
            this, 1, playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = Intent(ACTION_NEXT)
        val nextPendingIntent = PendingIntent.getBroadcast(
            this, 2, nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevIntent = Intent(ACTION_PREVIOUS)
        val prevPendingIntent = PendingIntent.getBroadcast(
            this, 3, prevIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(ACTION_STOP)
        val stopPendingIntent = PendingIntent.getBroadcast(
            this, 4, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Determine play/pause icon
        val playPauseIcon = if (isPlaying) R.drawable.pause else R.drawable.play

        // Build the notification
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.splash_screen_logo)
            .setContentTitle(currentSong?.title)
            .setContentText(currentSong?.artist)
            .setLargeIcon(artworkBitmap)
            .setContentIntent(contentPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(R.drawable.prev, "Previous", prevPendingIntent)
            .addAction(playPauseIcon, "Play/Pause", playPausePendingIntent)
            .addAction(R.drawable.next, "Next", nextPendingIntent)
            .addAction(R.drawable.close, "Stop", stopPendingIntent)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2) // Previous, Play/Pause, Next
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(notificationActionReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver: ${e.message}")
        }

        mediaSession.release()
        isServiceRunning = false
    }

    override fun onBind(intent: Intent?): IBinder? = null
}