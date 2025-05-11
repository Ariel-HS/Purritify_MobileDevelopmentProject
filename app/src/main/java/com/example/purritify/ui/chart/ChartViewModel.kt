package com.example.purritify.ui.chart

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.purritify.data.model.ChartSong
import com.example.purritify.retrofit.RetrofitClient
import com.example.purritify.ui.login.TokenManager
import kotlinx.coroutines.launch
import java.io.IOException

class ChartViewModel: ViewModel() {

    private val _songs = MutableLiveData<List<ChartSong>>()
    val songs: LiveData<List<ChartSong>> = _songs

    fun loadChartData(
        chartType: String,
        token: String
    ) {
        viewModelScope.launch {
            try {
                val api = RetrofitClient.getInstance().getMyApi()
                val response = when (chartType) {
                    "local" -> {
                        val profile = api.fetchProfileSuspend("Bearer $token")
                        val countryCode = profile.location
                        Log.d("ChartViewModel", countryCode)
                        api.fetchLocalSongs(countryCode)
                    }
                    else -> api.fetchGlobalSongs()
                }

                _songs.value = response

            } catch (e: IOException) {
                Log.e("ChartViewModel", "Network error: ${e.message}", e)
            } catch (e: Exception) {
                Log.e("ChartViewModel", "Unexpected error: ${e.message}", e)
            }
        }
    }
}