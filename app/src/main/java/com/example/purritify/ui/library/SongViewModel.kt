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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class SongViewModel(
    private val dao: SongDao
): ViewModel() {

    private val _isFilterLiked = MutableStateFlow(false)
    private val _songs = _isFilterLiked
        .flatMapLatest { isFilterLiked ->
            if (isFilterLiked) {
                val temp: Flow<List<Song>> = dao.searchLiked()
                Log.d("SongViewModel _songs Liked", temp.toString())
                return@flatMapLatest dao.searchLiked()
            }
            else {
                val temp: Flow<List<Song>> = dao.getAll()
                Log.d("SongViewModel _songs", temp.toString())

                return@flatMapLatest dao.getAll()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList<Song>())

    private val _state = MutableStateFlow(SongState())
    val state = combine(_state, _isFilterLiked, _songs) { state, isFilterLiked, songs ->
        state.copy(
            songs = songs,
            isFilterLiked = isFilterLiked
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SongState())

    fun onEvent(event: SongEvent) {
        when (event) {
            is SongEvent.DeleteSong -> {
                viewModelScope.launch {
                    dao.delete(event.song)
                }
            }
            SongEvent.HideDialog -> {
                _state.update { it.copy(
                    isAddingSong = false
                ) }
            }
            is SongEvent.IsFilterLiked -> {
                _isFilterLiked.value = event.liked
            }
            SongEvent.SaveSong -> {
                Log.d("SongViewModel", "SAVE CALLED")
                val title = state.value.title
                val artist = state.value.artist
                val pathArtwork = state.value.pathArtwork
                val pathFile = state.value.pathFile
                val duration = state.value.duration

                if (title.isBlank() || artist.isBlank() || pathArtwork.isBlank() || pathFile.isBlank()) {
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
                    liked = false
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