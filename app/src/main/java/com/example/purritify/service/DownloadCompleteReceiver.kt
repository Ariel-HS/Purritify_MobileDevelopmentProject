package com.example.purritify.service

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import com.example.purritify.data.model.ChartSong
import com.example.purritify.data.room.Song
import com.example.purritify.data.room.SongDatabase
import com.example.purritify.ui.library.SongViewModel
import com.example.purritify.ui.library.SongViewModelFactory
import com.example.purritify.ui.login.TokenManager
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Date

class DownloadCompleteReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE || context == null) return

        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (downloadId == -1L) return

        val sharedPrefs = context.getSharedPreferences("download_queue", Context.MODE_PRIVATE)
        val metaJson = sharedPrefs.getString("download_$downloadId", null) ?: return

        val jsonObj = JSONObject(metaJson)
        val audioId = jsonObj.getLong("audioId")
        val artworkId = jsonObj.getLong("artworkId")
        val songJson = jsonObj.getString("chartSong")

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        // Function to check if a download is completed
        fun isDownloadComplete(id: Long): Boolean {
            val cursor = downloadManager.query(DownloadManager.Query().setFilterById(id))
            cursor?.use {
                if (it.moveToFirst()) {
                    val statusIndex = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    if (statusIndex != -1) {
                        val status = it.getInt(statusIndex)
                        return status == DownloadManager.STATUS_SUCCESSFUL
                    } else {
                        Log.e("isDownloadComplete", "COLUMN_STATUS not found in cursor")
                    }
                }
            }
            return false
        }

        // wait for both download
        if (isDownloadComplete(audioId) && isDownloadComplete(artworkId)) {
            val gson = Gson()
            val chartSong = gson.fromJson(songJson, ChartSong::class.java)

            val fileUris = listOf(audioId, artworkId).mapNotNull { id ->
                val cursor = downloadManager.query(DownloadManager.Query().setFilterById(id))
                cursor?.use {
                    if (it.moveToFirst()) {
                        val uriIndex = it.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                        if (uriIndex != -1) {
                            it.getString(uriIndex)?.toUri()
                        } else {
                            Log.e("DownloadCompleteReceiver", "COLUMN_LOCAL_URI not found for download ID $id")
                        }
                    } else {
                        Log.e("DownloadCompleteReceiver", "No data found for download ID $id")
                    }
                }
            }

            if (fileUris.size != 2) return

            Log.d("DownloadCompleteReceiver", "FileUris: $fileUris")
            val audioUri = fileUris.find { it.toString().endsWith(".mp3") } ?: return
            val artworkUri = fileUris.find { !it.toString().endsWith(".mp3") } ?: return
            Log.d("DownloadCompleteReceiver", "Artwork: $artworkUri")

            val userEmail = TokenManager.getInstance(context).getUserEmail() ?: return
            val song = Song(
                title = chartSong.title,
                artist = chartSong.artist,
                pathArtwork = artworkUri.toString(),
                pathFile = audioUri.toString(),
                duration = chartSong.duration,
                liked = false,
                uploadDate = Date(),
                lastPlayed = null,
                userEmail = userEmail
            )

            CoroutineScope(Dispatchers.IO).launch {
                SongDatabase.getInstance(context).songDao.upsertSong(song)
                Log.d("DownloadReceiver", "Inserted song into DB: ${song.title}")
            }

            // Cleanup metadata
            sharedPrefs.edit().apply {
                remove("download_$audioId")
                remove("download_$artworkId")
                apply()
            }
        }
    }

}