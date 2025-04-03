package com.example.purritify.ui.library

import com.example.purritify.data.room.Song

data class SongState(
    val songs: Any = emptyList<Song>(),
    val title: String = "",
    val artist: String = "",
    val pathArtwork: String = "",
    val pathFile: String = "",
    val duration: String = "",
    val isAddingSong: Boolean = false,
    val isFilterLiked: Boolean = false
)
