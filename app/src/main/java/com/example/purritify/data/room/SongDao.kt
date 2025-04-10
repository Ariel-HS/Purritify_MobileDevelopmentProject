package com.example.purritify.data.room

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM song")
    fun getAll(): Flow<List<Song>>

    @Query("SELECT * FROM song WHERE userEmail = :email")
    fun getAllByUserEmail(email: String): Flow<List<Song>>

    @Query("SELECT * FROM Song ORDER BY uploadDate DESC")
    fun getAllSortedByUploadDate(): Flow<List<Song>>

    @Query("SELECT * FROM Song WHERE userEmail = :email ORDER BY uploadDate DESC")
    fun getAllSortedByUploadDateAndEmail(email: String): Flow<List<Song>>

    @Query("SELECT * FROM Song ORDER BY lastPlayed DESC LIMIT 5")
    fun getRecentlyPlayed(): Flow<List<Song>>

    @Query("SELECT * FROM Song WHERE userEmail = :email ORDER BY lastPlayed DESC LIMIT 5")
    fun getRecentlyPlayedByEmail(email: String): Flow<List<Song>>

    @Query("SELECT * FROM Song WHERE userEmail = :email ORDER BY uploadDate ASC")
    suspend fun getAllSortedByUploadDateOnce(email: String): List<Song>

    @Query("SELECT * FROM song WHERE id IN (:songIds)")
    fun selectAllByIds(songIds: IntArray): Flow<List<Song>>

    @Query("SELECT * FROM song WHERE id = :id")
    fun getSongById(id: Int): Flow<Song?>

    @Query("SELECT * FROM song WHERE title LIKE :title ")
    fun searchByTitle(title: String): Flow<List<Song>>

    @Query("SELECT * FROM song WHERE artist LIKE :artist ")
    fun searchByArtist(artist: String): Flow<List<Song>>

    @Query("SELECT * FROM song WHERE liked = 1 AND userEmail = :email ")
    fun searchLikedByEmail(email: String): Flow<List<Song>>

    @Upsert
    suspend fun upsertSong(song: Song)

    @Delete
    suspend fun delete(song: Song)
}