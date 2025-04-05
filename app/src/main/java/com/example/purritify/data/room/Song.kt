package com.example.purritify.data.room

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity
data class Song(
    val title: String,
    val artist: String,
    val pathArtwork: String,
    val pathFile: String,
    val duration: String,
    val liked: Boolean,

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val lastPlayed: Date? = null,
    val uploadDate: Date
)
