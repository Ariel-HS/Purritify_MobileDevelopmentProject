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
import androidx.core.net.toUri
import androidx.lifecycle.viewModelScope
import com.example.purritify.data.room.SongDatabase
import com.example.purritify.data.room.SongHistory
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

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

    private var currentAddedTime: Long = 0
    private var lastUpdateTime: Long = System.currentTimeMillis()
    private val _notifyUpdate = MutableLiveData<Unit>()
    val notifyUpdate: LiveData<Unit> get() = _notifyUpdate

    private var currentPlaylist: List<PlayableSong> = emptyList()
    private var currentPlaylistIndex: Int = -1
    private var currentMode: String = "offline"
    fun getMode(): String { return currentMode; }

    private val updateRunnable = object : Runnable {
        override fun run() {
            mediaPlayer?.let {
                _currentTime.value = it.currentPosition

                // Update added time
                val now = System.currentTimeMillis()
                val delta = now - lastUpdateTime
                currentAddedTime += delta.toInt()
                lastUpdateTime = now

                // Send progress update to service
                val intent = Intent(getApplication(), MediaPlayerService::class.java).apply {
                    action = "UPDATE_PROGRESS"
                    putExtra("currentPosition", it.currentPosition)
                    putExtra("duration", it.duration)
                }
                getApplication<Application>().startService(intent)

                handler.postDelayed(this, 500)

                if (currentAddedTime >= 60_000) { // full minute has passed
                    //notify to update recycler view

                    val song = currentSong.value
                    if (song != null) updateTimeOnDatabase(song.id)

                    currentAddedTime = 0
                }
            }
        }
    }

    // Broadcast receiver for notification actions
    private val playbackActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val song = currentSong.value
            if (song != null && currentMode == "offline") updateTimeOnDatabase(song.id)

            when (intent?.action) {
                MediaPlayerService.BROADCAST_PLAYBACK_STATE_CHANGED -> togglePause()
                MediaPlayerService.ACTION_NEXT -> playNextSong()
                MediaPlayerService.ACTION_PREVIOUS -> playPreviousSong()
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

    override fun onCleared() {
        super.onCleared()

        val song = currentSong.value
        if (song != null && currentMode == "offline") updateTimeOnDatabase(song.id)

        mediaPlayer?.release()
        handler.removeCallbacks(updateRunnable)

        try {
            val context = getApplication<Application>()
            LocalBroadcastManager.getInstance(context).unregisterReceiver(playbackActionReceiver)
        } catch (e: Exception) {
            Log.e("PlayerViewModel", "Error unregistering receiver: ${e.message}")
        }

        if (MediaPlayerService.isServiceRunning) {
            val context = getApplication<Application>()
            val intent = Intent(context, MediaPlayerService::class.java).apply {
                action = MediaPlayerService.ACTION_STOP
            }
            context.startService(intent)
        }
    }

    fun setCurrentSong(song: PlayableSong, mode: String = "offline") {
        Log.d("PlayerViewModel", "Playing song, current mode: $mode")
        _currentSong.value = song
        currentMode = mode
        updateMediaService(song, mode)

        if (mode == "offline") {
            checkUpdateMonthAndStreak(song.id)
        }
    }

    private fun updateMediaService(song: PlayableSong, mode: String) {
        val context = getApplication<Application>()
        val intent = Intent(context, MediaPlayerService::class.java).apply {
            action = "UPDATE_SONG"
            putExtra("title", song.title)
            putExtra("artist", song.artist)
            putExtra("artwork", song.pathArtwork)
            putExtra("id", song.id)
            putExtra("songMode", mode)
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

    fun setPlaylist(songs: List<PlayableSong>, currentSongId: Int) {
        currentPlaylist = songs
        currentPlaylistIndex = songs.indexOfFirst { it.id == currentSongId }
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

        currentSong.value?.let { song ->
            // should be fine
            handleLastPlayedUpdate(song as Song, songViewModel, this)
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

    fun playNextSong() {
        if (currentPlaylist.isEmpty() || currentPlaylistIndex == -1) return

        if (currentPlaylistIndex + 1 < currentPlaylist.size) {
            currentPlaylistIndex++
            val nextSong = currentPlaylist[currentPlaylistIndex]
            setCurrentSong(nextSong)

            // Get URI based on the song type
            val uri = if (nextSong.pathFile.startsWith("http")) {
                nextSong.pathFile.toUri()
            } else {
                nextSong.pathFile.toUri()
            }

            playSong(uri, null)

            // Broadcast that song has changed so PlayerFragment can update if visible
            val context = getApplication<Application>()
            val songChangedIntent = Intent(MediaPlayerService.BROADCAST_CURRENT_SONG_CHANGED)
            songChangedIntent.putExtra("songId", nextSong.id)
            LocalBroadcastManager.getInstance(context).sendBroadcast(songChangedIntent)
        }
    }

    fun playPreviousSong() {
        if (currentPlaylist.isEmpty() || currentPlaylistIndex <= 0) return

        currentPlaylistIndex--
        val prevSong = currentPlaylist[currentPlaylistIndex]
        setCurrentSong(prevSong)

        // Get URI based on the song type
        val uri = if (prevSong.pathFile.startsWith("http")) {
            prevSong.pathFile.toUri()
        } else {
            prevSong.pathFile.toUri()
        }

        playSong(uri, null)

        // Broadcast that song has changed so PlayerFragment can update if visible
        val context = getApplication<Application>()
        val songChangedIntent = Intent(MediaPlayerService.BROADCAST_CURRENT_SONG_CHANGED)
        songChangedIntent.putExtra("songId", prevSong.id)
        LocalBroadcastManager.getInstance(context).sendBroadcast(songChangedIntent)
    }

    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
    }

    private fun updateTimeOnDatabase(songId: Int) {
        val time = currentAddedTime/1000L
        val context = getApplication<Application>()
        val db = SongDatabase.getInstance(context)
        val songDao = db.songDao

        viewModelScope.launch {
            songDao.addTimeListenedById(songId, time)

            _notifyUpdate.value = Unit
        }

        currentAddedTime = 0
    }

    private fun checkUpdateMonthAndStreak(songId: Int) {
        val context = getApplication<Application>()
        val db = SongDatabase.getInstance(context)
        val songDao = db.songDao

        viewModelScope.launch {
            // get latest songHistory (this saves streakStart) (this took way too much effort :'/ )
            val listSongHistory: List<SongHistory> = songDao.getSongHistory(songId)

            val formatter = SimpleDateFormat("MM-yyyy", Locale.US)
            val now = formatter.format(Date())
            val nowInstant = Date().toInstant()

            var latestHistory = listSongHistory
                .mapNotNull { songHistory ->
                    formatter.parse(songHistory.month)?.let { date -> songHistory to date }
                }
                .maxByOrNull { it.second }
                ?.first

            if (latestHistory == null) {
                return@launch
            }

            if (latestHistory.month != now) {
                val newHistory = latestHistory.copy(
                    month = now
                )
                songDao.insertSongHistory(newHistory)

                // update latestHHistory
                latestHistory = newHistory
            }

            // update streak
            if (latestHistory.lastPlayed == null) {
                // update
                val newHistory = latestHistory.copy(
                    streakStart = Date()
                )
                songDao.updateSongHistory(newHistory)

                _notifyUpdate.value = Unit
                return@launch
            }

            val lastPlayed = latestHistory.lastPlayed!!.toInstant()
            val daysBetween = ChronoUnit.DAYS.between(lastPlayed, nowInstant)

            if (daysBetween > 1) {
                // update
                val newHistory = latestHistory.copy(
                    streakStart = Date()
                )
                songDao.updateSongHistory(newHistory)

                _notifyUpdate.value = Unit
            }
        }
    }
}