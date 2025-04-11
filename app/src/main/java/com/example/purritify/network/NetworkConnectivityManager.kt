package com.example.purritify.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class NetworkConnectivityManager(context: Context) {

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val _isNetworkAvailable = MutableLiveData<Boolean>()
    val isNetworkAvailable: LiveData<Boolean> = _isNetworkAvailable
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isNetworkMonitoringActive = true

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d("NetworkConnectivityManager", "Network callback: onAvailable")
            verifyInternetAccess()
        }

        override fun onLost(network: Network) {
            Log.d("NetworkConnectivityManager", "Network callback: onLost")
            _isNetworkAvailable.postValue(false)
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            Log.d("NetworkConnectivityManager", "Network capabilities changed")
            if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                verifyInternetAccess()
            }
        }
    }

    init {
        checkInitialNetworkState()
        registerNetworkCallback()
        startPeriodicConnectivityCheck()
    }

    private fun startPeriodicConnectivityCheck() {
        coroutineScope.launch {
            while (isActive && isNetworkMonitoringActive) {
                delay(10000) // Check internet connection time interval
                Log.d("NetworkConnectivityManager", "Running periodic connectivity check")
                verifyInternetAccess()
            }
        }
    }

    private fun verifyInternetAccess() {
        coroutineScope.launch {
            try {
                val isConnected = isNetworkConnected()
                Log.d("NetworkConnectivityManager", "Network connected check (verify): $isConnected")

                if (isConnected) {
                    val hasInternet = checkInternetConnection()
                    Log.d("NetworkConnectivityManager", "Internet connection verified: $hasInternet")
                    _isNetworkAvailable.postValue(hasInternet)
                } else {
                    _isNetworkAvailable.postValue(false)
                }
            } catch (e: Exception) {
                Log.e("NetworkConnectivityManager", "Error during network verification", e)
                _isNetworkAvailable.postValue(false)
            }
        }
    }

    private fun checkInternetConnection(): Boolean {
        return try {
            val connection = URL("https://www.google.com").openConnection() as HttpURLConnection
            connection.connectTimeout = 1500
            connection.readTimeout = 1500
            connection.requestMethod = "HEAD"
            connection.connect()
            val responseCode = connection.responseCode
            Log.d("NetworkConnectivityManager", "Internet check response code: $responseCode")
            responseCode == 200
        } catch (e: IOException) {
            Log.e("NetworkConnectivityManager", "No internet connection", e)
            false
        }
    }

    private fun checkInitialNetworkState() {
        val isConnected = isNetworkConnected()
        Log.d("NetworkConnectivityManager", "Initial network state: $isConnected")
        _isNetworkAvailable.postValue(isConnected)
    }

    private fun isNetworkConnected(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false

        val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))

        Log.d("NetworkConnectivityManager", "Network connected check: $hasInternet")
        return hasInternet
    }

    private fun registerNetworkCallback() {
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
        Log.d("NetworkConnectivityManager", "Network callback registered")
    }

    fun unregisterNetworkCallback() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            isNetworkMonitoringActive = false
            coroutineScope.cancel()
            Log.d("NetworkConnectivityManager", "Network callback unregistered and monitoring stopped")
        } catch (e: Exception) {
            Log.e("NetworkConnectivityManager", "Failed to unregister network callback", e)
        }
    }
}