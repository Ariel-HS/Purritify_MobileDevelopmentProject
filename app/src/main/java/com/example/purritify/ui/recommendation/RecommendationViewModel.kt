package com.example.purritify.ui.recommendation

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.purritify.data.model.ChartSong
import com.example.purritify.data.model.PlayableSong
import com.example.purritify.data.room.Song
import com.example.purritify.retrofit.RetrofitClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class RecommendationViewModel(
    private val likedSongsFlow: Flow<List<Song>>,
    private val localSongsFlow: Flow<List<Song>>,
    private val recommendationService: RecommendationService = RecommendationService()
) : ViewModel() {

    private val _recommendations = MutableLiveData<List<PlayableSong>>()
    val recommendations: LiveData<List<PlayableSong>> = _recommendations

    private val _isLoading = MutableLiveData<Boolean>(true)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    fun loadRecommendations(countryCode: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val likedSongs = likedSongsFlow.first()
                val localSongs = localSongsFlow.first()

                val api = RetrofitClient.getInstance().getMyApi()

                val globalChartSongs = try {
                    api.fetchGlobalSongs()
                } catch (e: Exception) {
                    Log.e("RecommendationVM", "Error fetching global songs: ${e.message}")
                    emptyList<ChartSong>()
                }
                val localChartSongs = try {
                    api.fetchLocalSongs(countryCode)
                } catch (e: Exception) {
                    Log.e("RecommendationVM", "Error fetching local songs: ${e.message}")
                    emptyList<ChartSong>()
                }

                val recommendations = recommendationService.getRecommendations(
                    likedSongs,
                    localSongs,
                    globalChartSongs,
                    localChartSongs
                )

                _recommendations.value = recommendations
            } catch (e: Exception) {
                Log.e("RecommendationVM", "Error generating recommendations: ${e.message}", e)
                _error.value = "Failed to generate recommendations: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
