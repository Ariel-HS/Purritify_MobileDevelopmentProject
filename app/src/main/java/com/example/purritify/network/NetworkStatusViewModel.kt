package com.example.purritify.network

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel

class NetworkStatusViewModel : ViewModel() {
    private var networkManager: NetworkConnectivityManager? = null

    val isNetworkAvailable: LiveData<Boolean>?
        get() = networkManager?.isNetworkAvailable

    fun initialize(networkManager: NetworkConnectivityManager) {
        this.networkManager = networkManager
    }

    override fun onCleared() {
        super.onCleared()
        networkManager?.unregisterNetworkCallback()
    }
}