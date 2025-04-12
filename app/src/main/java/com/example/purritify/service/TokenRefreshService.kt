package com.example.purritify.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.purritify.data.model.RefreshTokenRequest
import com.example.purritify.retrofit.RetrofitClient
import com.example.purritify.ui.login.TokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class TokenRefreshService : Service() {
    private val TAG = "TokenRefreshService"
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private val _tokenStatus = MutableLiveData<TokenStatus>()
    private var monitorJob: Job? = null

    companion object {
        // Check token time interval
        private val CHECK_INTERVAL = TimeUnit.MINUTES.toMillis(4)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "TokenRefreshService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Check if monitor is already running
        if (monitorJob == null || monitorJob?.isActive != true) {
            monitorJob = startTokenRefreshMonitor()
            Log.d(TAG, "TokenRefreshService started")
        } else {
            Log.d(TAG, "TokenRefreshService already running")
        }
        return START_STICKY
    }

    private fun startTokenRefreshMonitor(): Job {
        return serviceScope.launch {
            while (isActive) {
                Log.d(TAG, "Checking token at ${System.currentTimeMillis()}")
                val tokenManager = TokenManager.getInstance(applicationContext)
                val refreshToken = tokenManager.getRefreshToken()

                if (refreshToken != null) {
                    Log.d(TAG, "Refresh token found, attempting to refresh")
                    refreshToken(tokenManager)
                } else {
                    Log.d(TAG, "No refresh token found")
                    _tokenStatus.postValue(TokenStatus.MISSING)

                    // Stop service when no refresh token is found
                    stopSelf()
                    return@launch
                }

                delay(CHECK_INTERVAL)
            }
        }
    }

    private suspend fun verifyToken(token: String): Boolean {
        return try {
            val response = RetrofitClient.getInstance().getMyApi().verifyToken("Bearer $token")

            if (response.isSuccessful) {
                val body = response.body()
                Log.d(TAG, "Token verification result: ${body?.valid}")
                body?.valid ?: false
            } else {
                val errorBody = response.errorBody()?.string()
                Log.d(TAG, "Token verification failed with status: ${response.code()}, error: $errorBody")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying token", e)
            false
        }
    }

    private suspend fun refreshToken(tokenManager: TokenManager) {
        try {
            val refreshToken = tokenManager.getRefreshToken()
            if (refreshToken == null) {
                Log.e(TAG, "Refresh token is null, cannot refresh")
                _tokenStatus.postValue(TokenStatus.REFRESH_FAILED)
                handleLogout()
                return
            }

            val response = RetrofitClient.getInstance().getMyApi().refreshToken(
                RefreshTokenRequest(refreshToken)
            )

            if (response.isSuccessful && response.body() != null) {
                val refreshResponse = response.body()!!
                tokenManager.saveToken(refreshResponse.accessToken)
                tokenManager.saveRefreshToken(refreshResponse.refreshToken)
                Log.d(TAG, "Token refreshed successfully")
                Log.d(TAG, "New access token: ${tokenManager.getToken()}")
                Log.d(TAG, "New refresh token: ${tokenManager.getRefreshToken()}")
                _tokenStatus.postValue(TokenStatus.REFRESHED)
            } else {
                Log.e(TAG, "Failed to refresh token: ${response.code()}")
                _tokenStatus.postValue(TokenStatus.REFRESH_FAILED)
                handleLogout()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing token", e)
            _tokenStatus.postValue(TokenStatus.REFRESH_FAILED)
            handleLogout()
        }
    }

    private fun handleLogout() {
        // Force logout when refresh fails
        Log.d(TAG, "Forcing logout due to token refresh failure")
        val tokenManager = TokenManager.getInstance(applicationContext)
        tokenManager.clearToken()
        tokenManager.clearRefreshToken()
        tokenManager.clearUserEmail()
        Log.d(TAG, "User logged out and tokens cleared")
        Log.d(TAG, "Token: ${tokenManager.getToken()}")
        Log.d(TAG, "Refresh Token: ${tokenManager.getRefreshToken()}")
        Log.d(TAG, "User Email: ${tokenManager.getUserEmail()}")

        // Send broadcast to notify activities that token refresh failed and logout is needed
        val intent = Intent("com.example.purritify.TOKEN_REFRESH_FAILED")
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "TokenRefreshService destroyed")
        serviceScope.launch {
            this.coroutineContext.job.cancel()
        }
    }

    enum class TokenStatus {
        VALID,
        REFRESHED,
        REFRESH_FAILED,
        MISSING
    }
}