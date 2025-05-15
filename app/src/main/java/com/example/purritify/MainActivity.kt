package com.example.purritify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModelProvider
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.NavController
import androidx.navigation.findNavController
import com.example.purritify.databinding.ActivityMainBinding
import com.example.purritify.network.NetworkConnectivityManager
import com.example.purritify.network.NetworkStatusViewModel
import com.example.purritify.service.TokenRefreshService
import com.example.purritify.ui.login.LoginActivity
import com.example.purritify.ui.login.TokenManager
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Network connectivity components
    private lateinit var networkConnectivityManager: NetworkConnectivityManager
    private lateinit var networkStatusViewModel: NetworkStatusViewModel
    private var noInternetSnackbar: Snackbar? = null

    fun getNetworkStatusViewModel(): NetworkStatusViewModel {
        return networkStatusViewModel
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkTokenValidity()
        ensureTokenRefreshServiceRunning()
        registerTokenRefreshReceiver()

        // Setup network monitoring
        setupNetworkMonitoring()

        val navController = this.findNavController(R.id.fragment_container_view)

        // Handle deep links
        handleIntent(navController)

        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            // Pop player fragment before navigating
            navController.popBackStack(R.id.player, true)

            when (item.itemId) {
                R.id.home_page -> {
                    navController.navigate(R.id.home_page)
                    true
                }
                R.id.my_library -> {
                    navController.navigate(R.id.my_library)
                    true
                }
                R.id.profile -> {
                    navController.navigate(R.id.profile)
                    true
                }
                else -> false
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        val navController = this.findNavController(R.id.fragment_container_view)

        handleIntent(navController)
    }

    private fun handleIntent(navController: NavController) {
        // Handle deep links
        intent.data?.let { uri ->
            val pathSegments = uri.pathSegments
            if (uri.scheme == "purrytify" && uri.host == "song" && pathSegments.isNotEmpty()) {
                val songIdStr = pathSegments[0]
                val songId = songIdStr.toIntOrNull()
                if (songId != null) {
                    val action = MainNavDirections.actionGlobalPlayer(
                        songId = songId,
                        fromMiniplayer = false,
                        mode = "deepLink"
                    )

                    navController.navigate(action)
                } else {
                    Log.w("MainActivityDeeplink", "Invalid song ID in URI: $songIdStr")
                }
            } else {
                Log.w("MainActivityDeeplink", "Unhandled URI: $uri")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(tokenRefreshFailedReceiver)
        } catch (_: IllegalArgumentException) {
            Log.w("MainActivity", "Token refresh receiver was not registered or already unregistered")
        }

        // Unregister network callback
        networkConnectivityManager.unregisterNetworkCallback()
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

    private fun setupNetworkMonitoring() {
        networkConnectivityManager = NetworkConnectivityManager(applicationContext)
        networkStatusViewModel = ViewModelProvider(this)[NetworkStatusViewModel::class.java]
        networkStatusViewModel.initialize(networkConnectivityManager)

        binding.root.post {
            networkStatusViewModel.isNetworkAvailable?.observe(this) { isAvailable ->
                if (isAvailable) {
                    // Internet is available
                    noInternetSnackbar?.dismiss()
                    noInternetSnackbar = null
                } else {
                    // No internet
                    if (noInternetSnackbar == null) {
                        noInternetSnackbar = Snackbar.make(
                            binding.root,
                            "No internet connection",
                            Snackbar.LENGTH_INDEFINITE
                        )

                        val snackbarView = noInternetSnackbar?.view

                        // Set close button
                        noInternetSnackbar?.setAction("\u00D7") {
                            noInternetSnackbar?.dismiss()
                            noInternetSnackbar = null
                        }

                        val textView = snackbarView?.findViewById<TextView>(com.google.android.material.R.id.snackbar_action)
                        textView?.textSize = 22f

                        noInternetSnackbar?.show()
                    }
                }
            }
        }
    }
}
