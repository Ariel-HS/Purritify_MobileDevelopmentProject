package com.example.purritify.data.room

import java.util.Date

data class ArtistStat(
    val artist: String,
    val totalTime: Int
)

data class SongStat(
    val title: String,
    val pathArtwork: String,
    val totalTime: Int
)

data class UserStat(
    val totalTime: Int
)

data class StreakStat(
    val title: String,
    val artist: String,
    val pathArtwork: String,
    val streakLength: Int,
    val streakStart: Date,
    val streakEnd: Date
)

data class MonthlyStat(
    val month: String,
    val timeListened: UserStat,
    val topSong: SongStat,
    val topArtist: ArtistStat,
    val streakSong: StreakStat?
)

