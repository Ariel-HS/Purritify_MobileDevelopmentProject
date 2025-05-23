package com.example.purritify.data.room

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import java.util.Locale

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

    @Query("SELECT EXISTS(SELECT 1 FROM song WHERE title = :title)")
    suspend fun isSongExistByTitle(title: String): Boolean

    @Query("SELECT * FROM song WHERE artist LIKE :artist ")
    fun searchByArtist(artist: String): Flow<List<Song>>

    @Query("SELECT * FROM song WHERE liked = 1 AND userEmail = :email ")
    fun searchLikedByEmail(email: String): Flow<List<Song>>

    @Query("SELECT * FROM song_history WHERE id = :songId")
    suspend fun getSongHistory(songId: Int): List<SongHistory>

    @Query("SELECT * FROM song_history WHERE id = :songId AND month = :month")
    suspend fun getSongHistoryByMonth(songId: Int, month: String): SongHistory?

    @Query("Select DISTINCT month FROM song_history WHERE userEmail = :email")
    fun getMonths(email: String): List<String>

    @Query("""
        SELECT artist, SUM(timeListened) AS totalTime
        FROM song_history
        WHERE userEmail = :email AND month = :month
        GROUP BY artist 
        ORDER BY totalTime DESC
        LIMIT 5
    """)
    fun getTopArtistByMonth(email: String, month: String): List<ArtistStat>

    @Query("""
        SELECT title, pathArtwork, SUM(timeListened) AS totalTime
        FROM song_history
        WHERE userEmail = :email AND month = :month
        GROUP BY title, pathArtwork 
        ORDER BY totalTime DESC
        LIMIT 5
    """)
    fun getTopSongByMonth(email: String, month: String): List<SongStat>

    @Query("""
        SELECT SUM(timeListened) AS totalTime
        FROM song_history
        WHERE userEmail = :email and month = :month
        LIMIT 5
    """)
    fun getTimeListenedByMonth(email: String, month: String): UserStat

    @Query("""
        SELECT 
            title, 
            artist,
            pathArtwork,
            CAST(julianday(lastPlayed) - julianday(streakStart) AS INTEGER) AS streakLength,
            streakStart,
            lastPlayed AS streakEnd
        FROM song_history
        WHERE userEmail = :email
            AND lastPlayed IS NOT NULL 
            AND streakStart IS NOT NULL 
            AND CAST(julianday(lastPlayed) - julianday(streakStart) AS INTEGER) >= 2
        ORDER BY streakEnd DESC
    """)
    fun getStreakData(email: String): List<StreakStat>

    @Upsert
    suspend fun upsertSongHelper(song: Song): Long

    @Insert
    suspend fun insertSongHistory(history: SongHistory)

    @Update
    suspend fun updateSongHistory(history: SongHistory)

    @Query("""
        UPDATE song_history
        SET timeListened = timeListened + :time
        WHERE id = :id
    """)
    suspend fun addTimeListenedById(id: Int, time: Long)

    @Transaction
    suspend fun upsertSong(song: Song) {
        val songId = upsertSongHelper(song).toInt()

        val formatter = SimpleDateFormat("MM-yyyy", Locale.US)
        val now = formatter.format(Date())

        // TODO check if need to update every month or just 'now' month
        val songExist = getSongHistoryByMonth(songId, now)

        if (songExist == null) {
            val newHistory = SongHistory(
                title = song.title,
                artist = song.artist,
                pathArtwork = song.pathArtwork,
                userEmail = song.userEmail,
                id = songId,
                month = now,
                streakStart = song.streakStart,
                lastPlayed = song.lastPlayed,
                timeListened = 0
            )
            insertSongHistory(newHistory)
        } else {
            val updated = songExist.copy(
                title = song.title,
                artist = song.artist,
                pathArtwork = song.pathArtwork,
                userEmail = song.userEmail,
                lastPlayed = song.lastPlayed
            )
            updateSongHistory(updated)
        }
    }

    @Delete
    suspend fun delete(song: Song)
}