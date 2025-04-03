package com.example.purritify.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.purritify.data.LoginDataSource
import com.example.purritify.data.LoginRepository
import com.example.purritify.data.room.SongDao
import com.example.purritify.ui.login.LoginViewModel

class SongViewModelFactory(private val dao: SongDao) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SongViewModel::class.java)) {
            return SongViewModel(
                dao
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}