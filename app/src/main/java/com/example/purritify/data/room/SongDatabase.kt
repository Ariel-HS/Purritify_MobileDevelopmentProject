package com.example.purritify.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.Calendar
import java.util.Locale

val currentMonth: String = run {
    val cal = Calendar.getInstance()
    val year = cal.get(Calendar.YEAR)
    val month = cal.get(Calendar.MONTH) + 1
    String.format(Locale.US, "%02d-%04d", month, year)
}

@Database(entities = [Song::class, SongHistory::class], version = 4)
@TypeConverters(Converters::class)
abstract class SongDatabase: RoomDatabase() {
    abstract val songDao: SongDao

    companion object {
        @Volatile
        private var INSTANCE: SongDatabase? = null

        fun getInstance(context: Context): SongDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SongDatabase::class.java,
                    "songs.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Song ADD COLUMN lastPlayed INTEGER")
                db.execSQL("ALTER TABLE Song ADD COLUMN uploadDate INTEGER")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Song ADD COLUMN userEmail TEXT")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {

                db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS 'song_history' (
                    'title' TEXT NOT NULL,
                    'artist' TEXT NOT NULL,
                    'pathArtwork' TEXT NOT NULL,
                    'userEmail' TEXT NOT NULL,
                    'id' INTEGER NOT NULL,
                    'month' TEXT NOT NULL,
                    'streakStart' TEXT,
                    'lastPlayed' TEXT,
                    'timeListened' INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY('id', 'month'),
                    FOREIGN KEY('id') REFERENCES 'Song'('id') ON DELETE CASCADE
                )
                """
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS 'index_song_history_id' ON 'song_history' ('id')")

                db.execSQL(
                    """
                    INSERT INTO song_history (title, artist, pathArtwork, userEmail, id, month, streakStart, lastPlayed, timeListened)
                    SELECT title, artist, pathArtwork, userEmail, id, '$currentMonth', lastPlayed, lastPlayed, 0 FROM Song
                    """
                )
            }
        }
    }
}