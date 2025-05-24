package com.example.purritify.data.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.purritify.data.model.PlayableSong
import java.util.Date

@Entity(
    tableName = "song_history",
    primaryKeys = ["id", "month"],
    foreignKeys = [
        ForeignKey(
            entity = Song::class,
            parentColumns = ["id"],
            childColumns = ["id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["id"])]
)
data class SongHistory(
    val title: String,
    val artist: String,
    val pathArtwork: String,
    val userEmail: String,

    val id: Int,
    val month: String,

    val streakStart: Date? = null,
    val lastPlayed: Date? = null,
    val timeListened: Long = 0,
)
