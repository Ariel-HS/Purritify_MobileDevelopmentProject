package com.example.purritify.ui.recommendation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.purritify.data.room.SongDao

class RecommendationViewModelFactory(
    private val songDao: SongDao,
    private val userEmail: String
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RecommendationViewModel::class.java)) {
            val likedSongs = songDao.searchLikedByEmail(userEmail)
            val allSongs = songDao.getAllByUserEmail(userEmail)

            @Suppress("UNCHECKED_CAST")
            return RecommendationViewModel(likedSongs, allSongs) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
