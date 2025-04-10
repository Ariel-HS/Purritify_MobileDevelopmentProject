package com.example.purritify

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.NavController
import androidx.navigation.Navigation
import com.example.purritify.databinding.ActivityMainBinding
import androidx.navigation.findNavController
import androidx.navigation.ui.NavigationUI
import com.example.purritify.service.TokenRefreshService
import com.example.purritify.ui.login.LoginActivity
import com.example.purritify.ui.login.TokenManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkTokenValidity()
        ensureTokenRefreshServiceRunning()
        registerTokenRefreshReceiver()

        val navController = this.findNavController(R.id.fragment_container_view)
        NavigationUI.setupWithNavController(binding.bottomNavigationView, navController)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(tokenRefreshFailedReceiver)
        } catch (e: IllegalArgumentException) {
            Log.w("MainActivity", "Token refresh receiver was not registered or already unregistered")
        }
    }

    private fun checkTokenValidity() {
        val tokenManager = TokenManager.getInstance(applicationContext)
        if (tokenManager.getToken() == null) {
            navigateToLogin()
        }
    }

    private fun navigateToLogin() {
        startActivity(Intent(this, LoginActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }

    private fun ensureTokenRefreshServiceRunning() {
        try {
            val serviceIntent = Intent(this, TokenRefreshService::class.java)
            startService(serviceIntent)
            Log.d("MainActivity", "Token refresh service started")
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to start token refresh service", e)
        }
    }

    private fun registerTokenRefreshReceiver() {
        try {
            LocalBroadcastManager.getInstance(this).registerReceiver(
                tokenRefreshFailedReceiver,
                IntentFilter("com.example.purritify.TOKEN_REFRESH_FAILED")
            )
            Log.d("MainActivity", "Token refresh receiver registered")
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to register token refresh receiver", e)
        }
    }

    private val tokenRefreshFailedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.purritify.TOKEN_REFRESH_FAILED") {
                // Handle token refresh failure - redirect to login
                startActivity(Intent(this@MainActivity, LoginActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                finish()
            }
        }
    }

    fun stopTokenRefreshService() {
        try {
            val serviceIntent = Intent(this, TokenRefreshService::class.java)
            stopService(serviceIntent)
            Log.d("MainActivity", "Token refresh service stopped")
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to stop token refresh service", e)
        }
    }
}
