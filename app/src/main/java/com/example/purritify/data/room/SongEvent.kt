package com.example.purritify.data.room

import kotlin.time.Duration

sealed interface SongEvent {
    data object SaveSong: SongEvent
    data class SetTitle(val title: String): SongEvent
    data class SetArtist(val artist: String): SongEvent
    data class SetPathArtwork(val path: String): SongEvent
    data class SetPathFile(val path: String): SongEvent
    data class SetDuration(val duration: String): SongEvent

    data object ShowDialog: SongEvent
    data object HideDialog: SongEvent
    data class IsFilterLiked(val liked: Boolean): SongEvent

    data class DeleteSong(val song: Song): SongEvent
}