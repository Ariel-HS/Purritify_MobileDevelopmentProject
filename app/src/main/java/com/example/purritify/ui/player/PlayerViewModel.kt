package com.example.purritify.ui.player

import android.app.Application
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.purritify.R

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
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

    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying == true
    }

    fun playSong(uri: Uri = Uri.EMPTY) {
        // TODO: URI
        mediaPlayer?.release()

        // For testing: always play the sample song
        mediaPlayer = MediaPlayer.create(getApplication(), R.raw.sample).apply {
            _duration.value = duration

            start()

            _isPaused.value = false
            handler.post(updateRunnable)

            setOnCompletionListener {
                _isPaused.value = true
            }
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
