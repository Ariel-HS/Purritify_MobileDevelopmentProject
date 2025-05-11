package com.example.purritify.data.room

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.purritify.data.model.PlayableSong
import java.util.Date

@Entity
data class Song(
    override val title: String,
    override val artist: String,
    override val pathArtwork: String,
    override val pathFile: String,
    override val duration: String,
    val liked: Boolean,
    val userEmail: String,

    @PrimaryKey(autoGenerate = true)
    override val id: Int = 0,

    val lastPlayed: Date? = null,
    val uploadDate: Date
) : PlayableSong {
//    override val isOnline: Boolean = false
}
