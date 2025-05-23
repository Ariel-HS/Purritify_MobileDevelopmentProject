package com.example.purritify.ui.recommendation

import android.util.Log
import com.example.purritify.data.model.ChartSong
import com.example.purritify.data.model.PlayableSong
import com.example.purritify.data.room.Song

class RecommendationService {

    fun getRecommendations(
        likedSongs: List<Song>,
        localSongs: List<Song>,
        globalChartSongs: List<ChartSong>,
        localChartSongs: List<ChartSong>,
        limit: Int = 10
    ): List<PlayableSong> {
        if (likedSongs.isEmpty()) {
            return generateDefaultRecommendations(globalChartSongs, limit)
        }

        val artistCounts = mutableMapOf<String, Int>()

        likedSongs.forEach { song ->
            val artists = song.artist.split(",").map { it.trim() }

            artists.forEach { artist ->
                artistCounts[artist] = (artistCounts[artist] ?: 0) + 1
            }
        }

        val topArtists = artistCounts.entries
            .sortedByDescending { it.value }
            .take(3)
            .map { it.key }

        if (topArtists.isEmpty()) {
            return generateDefaultRecommendations(globalChartSongs, limit)
        }

        Log.d("RecommendationService", "Top artists: $topArtists")

        val recommendations = mutableListOf<PlayableSong>()

        localSongs
            .filter { song ->
                topArtists.any { artist ->
                    song.artist.contains(artist, ignoreCase = true)
                }
            }
            .filter { it.id !in likedSongs.map { liked -> liked.id } }
            .take(limit/2)
            .let { recommendations.addAll(it) }

        val chartSongs = (globalChartSongs + localChartSongs)
            .filter { song ->
                topArtists.any { artist ->
                    song.artist.contains(artist, ignoreCase = true)
                }
            }
            .take(limit - recommendations.size)

        recommendations.addAll(chartSongs)

        if (recommendations.size < limit) {
            val remaining = limit - recommendations.size
            recommendations.addAll(generateDefaultRecommendations(
                globalChartSongs, remaining,
                exclude = recommendations.map { it.id }
            ))
        }

        return recommendations.take(limit)
    }

    private fun generateDefaultRecommendations(
        globalChartSongs: List<ChartSong>,
        limit: Int,
        exclude: List<Int> = emptyList()
    ): List<PlayableSong> {
        val recommendations = mutableListOf<PlayableSong>()

        val chartSongs = globalChartSongs
            .filter { it.id !in exclude }
            .take(limit)

        recommendations.addAll(chartSongs)

        return recommendations
    }
}
