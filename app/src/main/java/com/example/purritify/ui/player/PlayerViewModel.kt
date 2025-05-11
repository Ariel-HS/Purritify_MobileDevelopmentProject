package com.example.purritify.ui.player

import android.app.Application
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.purritify.R
import com.example.purritify.data.model.PlayableSong
import com.example.purritify.data.room.Song
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

    fun setCurrentSong(song: PlayableSong) {
        _currentSong.value = song
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
                }
                tempMediaPlayer.setOnCompletionListener {
                    _isPaused.value = true
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
                    tempMediaPlayer.setOnCompletionListener {
                        _isPaused.value = true
                    }
                }
            }
            mediaPlayer = tempMediaPlayer
        } catch (_: Exception) {
            Log.d("PlayerViewModel", "Song file doesn't exist at $uri, defaulting to sample")

            mediaPlayer = MediaPlayer.create(context, R.raw.sample).apply {
                start()
                _duration.value = duration
                _isPaused.value = false
                handler.post(updateRunnable)
                setOnCompletionListener {
                    _isPaused.value = true
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
        }
    }

    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
    }

    override fun onCleared() {
        super.onCleared()
        mediaPlayer?.release()
        handler.removeCallbacks(updateRunnable)
    }
}
