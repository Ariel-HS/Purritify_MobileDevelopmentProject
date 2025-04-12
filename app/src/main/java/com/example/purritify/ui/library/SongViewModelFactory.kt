package com.example.purritify.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.purritify.data.room.SongDao

class SongViewModelFactory(
    private val dao: SongDao,
    private val email: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SongViewModel::class.java)) {
            return SongViewModel(
                dao,
                email
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}