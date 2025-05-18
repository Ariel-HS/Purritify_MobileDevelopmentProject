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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
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
import java.util.Locale

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

        // Action for app closed
        const val ACTION_APP_CLOSED = "com.example.purritify.APP_CLOSED"

        // Flag to track service state
        var isServiceRunning = false
    }

    private lateinit var mediaSession: MediaSessionCompat
    private var currentSong: PlayableSong? = null
    private var isPlaying = false
    private var artworkBitmap: Bitmap? = null
    private var currentSongMode: String = "offline"

    private val notificationActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_PLAY_PAUSE -> {
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
                ACTION_STOP, ACTION_APP_CLOSED -> {
                    val stopAppIntent = Intent("com.example.purritify.STOP_APP")
                    LocalBroadcastManager.getInstance(this@MediaPlayerService)
                        .sendBroadcast(stopAppIntent)
                    stopSelf()
                }
            }
        }
    }

    private var currentPosition = 0
    private var duration = 0
    private val updateIntervalMs = 1000L // Update progress every second
    private val handler = Handler(Looper.getMainLooper())

    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            updateNotification()

            if (isPlaying) {
                handler.postDelayed(this, updateIntervalMs)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        mediaSession = MediaSessionCompat(this, "PurrytifyMediaSession")

        val intentFilter = IntentFilter().apply {
            addAction(ACTION_PLAY_PAUSE)
            addAction(ACTION_NEXT)
            addAction(ACTION_PREVIOUS)
            addAction(ACTION_STOP)
            addAction(ACTION_APP_CLOSED)
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

                currentSongMode = intent.getStringExtra("songMode") ?: "offline"

                updateCurrentSong(songTitle, songArtist, songArtwork, songId)
            }
            "UPDATE_PLAYBACK_STATE" -> {
                isPlaying = intent.getBooleanExtra("isPlaying", false)

                if (isPlaying) {
                    handler.removeCallbacks(updateProgressRunnable)
                    handler.post(updateProgressRunnable)
                } else {
                    handler.removeCallbacks(updateProgressRunnable)
                }

                updateNotification()
            }
            "UPDATE_PROGRESS" -> {
                currentPosition = intent.getIntExtra("currentPosition", 0)
                duration = intent.getIntExtra("duration", 0)
                updateNotification()
            }
            ACTION_APP_CLOSED -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        return START_STICKY
    }

    private fun updateCurrentSong(title: String, artist: String, artwork: String, id: Int) {
        currentSong = object : PlayableSong {
            override val title: String = title
            override val artist: String = artist
            override val pathArtwork: String = artwork
            override val pathFile: String = ""
            override val duration: String = ""
            override val id: Int = id
        }

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
            putExtra("songMode", currentSongMode)
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

        val currentTimeFormatted = formatTime(currentPosition)
        val durationFormatted = formatTime(duration)

        val mediaMetadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentSong?.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentSong?.artist)
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artworkBitmap)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration.toLong())
            .build()

        mediaSession.setMetadata(mediaMetadata)

        // Update playback state with progress
        val stateBuilder = PlaybackStateCompat.Builder()
            .setState(
                if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                currentPosition.toLong(),
                1.0f
            )
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            )

        mediaSession.setPlaybackState(stateBuilder.build())

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
            .setSubText("$currentTimeFormatted / $durationFormatted")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()

        handler.removeCallbacks(updateProgressRunnable)

        try {
            unregisterReceiver(notificationActionReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver: ${e.message}")
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)

        mediaSession.release()
        isServiceRunning = false
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun formatTime(timeMs: Int): String {
        val totalSeconds = timeMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}