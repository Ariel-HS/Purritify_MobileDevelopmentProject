package com.example.purritify.ui.library

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.purritify.data.room.Song
import com.example.purritify.data.room.SongDao
import com.example.purritify.data.room.SongEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class SongViewModel(
    private val dao: SongDao,
    private val userEmail: String
): ViewModel() {

    private val _isFilterLiked = MutableStateFlow(false)

    private val _songs = _isFilterLiked
        .flatMapLatest { isFilterLiked ->
            if (isFilterLiked) {
                val temp: Flow<List<Song>> = dao.searchLikedByEmail(userEmail)
                Log.d("SongViewModel _songs Liked", temp.toString())
                return@flatMapLatest dao.searchLikedByEmail(userEmail)
            }
            else {
                val temp: Flow<List<Song>> = dao.getAllByUserEmail(userEmail)
                Log.d("SongViewModel _songs", temp.toString())

                return@flatMapLatest dao.getAllByUserEmail(userEmail)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    private val _newSongs = dao.getAllSortedByUploadDateAndEmail(userEmail).stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())
    private val _recentlyPlayedSongs = dao.getRecentlyPlayedByEmail(userEmail).stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    private val _state = MutableStateFlow(SongState())
    val state = combine(_state, _isFilterLiked, _songs) { state, isFilterLiked, songs ->
        state.copy(
            songs = songs,
            isFilterLiked = isFilterLiked
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SongState())

    val newSongsState = combine(_state, _newSongs) { state, songs ->
        state.copy(songs = songs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SongState())

    val recentlyPlayedState = combine(_state, _recentlyPlayedSongs) { state, songs ->
        state.copy(songs = songs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SongState())

    // Profile Statistics
    fun getSongCount(): Flow<Int> { return dao.getAllSortedByUploadDateAndEmail(userEmail).map { songs -> songs.size }}
    fun getLikedSongCount(): Flow<Int> { return dao.searchLikedByEmail(userEmail).map { likedSongs -> likedSongs.size }}
    fun getListenedSongCount(): Flow<Int> { return dao.getAllSortedByUploadDateAndEmail(userEmail).map { songs -> songs.count { it.lastPlayed != null }}}

    // Player
    fun getAllSongs(): Flow<List<Song>> {
        return dao.getAllSortedByUploadDateAndEmail(userEmail)
    }

    fun onEvent(event: SongEvent) {
        Log.d("SongViewModel", event.toString())
        when (event) {
            is SongEvent.DeleteSong -> {
                viewModelScope.launch {
                    dao.delete(event.song)
                }
            }
            is SongEvent.UpsertSong -> {
                viewModelScope.launch {
                    dao.upsertSong(event.song)
                }
            }
            SongEvent.HideDialog -> {
                _state.update { it.copy(
                    isAddingSong = false
                ) }
            }
            is SongEvent.IsFilterLiked -> {
                Log.d("SongViewModel", "Liked: ${event.liked}")
                _isFilterLiked.value = event.liked
            }
            SongEvent.SaveSong -> {
                Log.d("SongViewModel", "SAVE CALLED")
                val title = state.value.title
                val artist = state.value.artist
                val pathArtwork = state.value.pathArtwork
                val pathFile = state.value.pathFile
                val duration = state.value.duration

                if (title.isBlank() || artist.isBlank() || pathArtwork.isBlank() || pathFile.isBlank() || duration.isBlank()) {
                    Log.d("SongViewModel", "SOMETHING'S BLANK $title, $artist, $pathArtwork, $pathFile")
                    // TODO Create Toast (and Handle Duration maybe?)
                    return
                }

                val song = Song(
                    title = title,
                    artist = artist,
                    pathArtwork = pathArtwork,
                    pathFile = pathFile,
                    duration = duration,
                    liked = false,
                    uploadDate = Date(),
                    lastPlayed = null,
                    userEmail = userEmail
                )

                viewModelScope.launch {
                    dao.upsertSong(song)
                }

                _state.update { it.copy(
                    isAddingSong = false,
                    title = "",
                    artist = "",
                    pathArtwork = "",
                    pathFile = ""
                ) }
            }
            is SongEvent.SetArtist -> {
                _state.update { it.copy(
                    artist = event.artist
                ) }
            }
            is SongEvent.SetPathArtwork -> {
                _state.update { it.copy(
                    pathArtwork = event.path
                ) }
            }
            is SongEvent.SetPathFile -> {
                _state.update { it.copy(
                    pathFile = event.path
                ) }
            }
            is SongEvent.SetTitle -> {
                _state.update { it.copy(
                    title = event.title
                ) }
            }
            is SongEvent.UpdateLastPlayed -> {
                viewModelScope.launch {
                    val updatedSong = event.song.copy(lastPlayed = Date())
                    dao.upsertSong(updatedSong)
                }
            }
            is SongEvent.SetDuration -> {
                _state.update { it.copy(
                    duration = event.duration
                ) }
            }
            SongEvent.ShowDialog -> {
                _state.update { it.copy(
                    isAddingSong = true
                ) }
            }
        }

    }
}