package com.example.purritify.ui.player

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.purritify.R
import com.example.purritify.data.model.PlayableSong
import com.example.purritify.data.room.Song
import com.example.purritify.service.MediaPlayerService
import com.example.purritify.ui.library.SongViewModel

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val _currentSong = MutableLiveData<PlayableSong?>()
    val currentSong: LiveData<PlayableSong?> get() = _currentSong

    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    private val _isPaused = MutableLiveData(false)
    val isPaused: LiveData<Boolean> get() = _isPaused

    private val _duration = MutableLiveData(0)
    val duration: LiveData<Int> get() = _duration

    private val _currentTime = MutableLiveData(0)
    val currentTime: LiveData<Int> get() = _currentTime

    private val updateRunnable = object : Runnable {
        override fun run() {
            mediaPlayer?.let {
                _currentTime.value = it.currentPosition
                handler.postDelayed(this, 500)
            }
        }
    }

    // Broadcast receiver for notification actions
    private val playbackActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                MediaPlayerService.BROADCAST_PLAYBACK_STATE_CHANGED -> togglePause()
                MediaPlayerService.ACTION_NEXT -> {
                    // Handle next action - This would need to be implemented
                    // based on how your app handles song switching
                }
                MediaPlayerService.ACTION_PREVIOUS -> {
                    // Handle previous action - This would need to be implemented
                    // based on how your app handles song switching
                }
            }
        }
    }

    init {
        // Register broadcast receiver for notification controls
        val context = getApplication<Application>()
        val filter = IntentFilter().apply {
            addAction(MediaPlayerService.BROADCAST_PLAYBACK_STATE_CHANGED)
            addAction(MediaPlayerService.ACTION_NEXT)
            addAction(MediaPlayerService.ACTION_PREVIOUS)
        }
        LocalBroadcastManager.getInstance(context).registerReceiver(playbackActionReceiver, filter)
    }

    fun setCurrentSong(song: PlayableSong) {
        _currentSong.value = song
        // Update notification with new song info
        updateMediaService(song)
    }

    private fun updateMediaService(song: PlayableSong) {
        val context = getApplication<Application>()
        val intent = Intent(context, MediaPlayerService::class.java).apply {
            action = "UPDATE_SONG"
            putExtra("title", song.title)
            putExtra("artist", song.artist)
            putExtra("artwork", song.pathArtwork)
            putExtra("id", song.id)
        }

        context.startForegroundService(intent)
    }

    private fun updatePlaybackState() {
        val context = getApplication<Application>()
        val intent = Intent(context, MediaPlayerService::class.java).apply {
            action = "UPDATE_PLAYBACK_STATE"
            putExtra("isPlaying", !(_isPaused.value ?: true))
        }
        context.startService(intent)
    }

    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying == true
    }

    fun playSong(uri: Uri, songViewModel: SongViewModel?) {
        Log.d("PlayerViewModel", "Play Song $uri")
        mediaPlayer?.release()

        val context = getApplication<Application>()
        val contentResolver = context.contentResolver
        var tempMediaPlayer = MediaPlayer()

        try {
            if (uri.scheme == "http" || uri.scheme == "https") {
                tempMediaPlayer.setDataSource(uri.toString())
                tempMediaPlayer.setOnPreparedListener {
                    it.start()
                    _duration.value = it.duration
                    _isPaused.value = false
                    handler.post(updateRunnable)
                    updatePlaybackState()
                }
                tempMediaPlayer.setOnCompletionListener {
                    _isPaused.value = true
                    updatePlaybackState()
                }
                tempMediaPlayer.prepareAsync()
            } else {
                contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    tempMediaPlayer.setDataSource(pfd.fileDescriptor)
                    tempMediaPlayer.prepare()
                    tempMediaPlayer.start()
                    _duration.value = tempMediaPlayer.duration
                    _isPaused.value = false
                    handler.post(updateRunnable)
                    updatePlaybackState()
                    tempMediaPlayer.setOnCompletionListener {
                        _isPaused.value = true
                        updatePlaybackState()
                    }
                }
            }
            mediaPlayer = tempMediaPlayer
        } catch (e: Exception) {
            Log.d("PlayerViewModel", "Song file doesn't exist at $uri, defaulting to sample")

            mediaPlayer = MediaPlayer.create(context, R.raw.sample).apply {
                start()
                _duration.value = duration
                _isPaused.value = false
                handler.post(updateRunnable)
                updatePlaybackState()
                setOnCompletionListener {
                    _isPaused.value = true
                    updatePlaybackState()
                }
            }
        }

        // Update lastPlayed value in db
        if (songViewModel == null) {
            return
        }

        currentSong.value?.let {
            if (it is Song) handleLastPlayedUpdate(it, songViewModel, this)
        }
    }

    fun togglePause() {
        mediaPlayer?.let {
            if (_isPaused.value == true) {
                it.start()
                _isPaused.value = false
            } else {
                it.pause()
                _isPaused.value = true
            }
            updatePlaybackState()
        }
    }

    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
    }

    override fun onCleared() {
        super.onCleared()
        mediaPlayer?.release()
        handler.removeCallbacks(updateRunnable)

        // Unregister broadcast receiver
        try {
            val context = getApplication<Application>()
            LocalBroadcastManager.getInstance(context).unregisterReceiver(playbackActionReceiver)
        } catch (e: Exception) {
            Log.e("PlayerViewModel", "Error unregistering receiver: ${e.message}")
        }

        // Stop the service when the ViewModel is cleared
        if (MediaPlayerService.isServiceRunning) {
            val context = getApplication<Application>()
            val intent = Intent(context, MediaPlayerService::class.java).apply {
                action = MediaPlayerService.ACTION_STOP
            }
            context.startService(intent)
        }
    }
}