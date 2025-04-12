package com.example.purritify.ui.login

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.example.purritify.MainActivity
import com.example.purritify.R
import com.example.purritify.databinding.ActivityLoginBinding
import com.example.purritify.network.NetworkConnectivityManager
import com.example.purritify.network.NetworkStatusViewModel
import com.google.android.material.snackbar.Snackbar


class LoginActivity : AppCompatActivity() {
    private lateinit var loginViewModel: LoginViewModel
    private lateinit var binding: ActivityLoginBinding

    // Network connectivity components
    private lateinit var networkConnectivityManager: NetworkConnectivityManager
    private lateinit var networkStatusViewModel: NetworkStatusViewModel
    private var noInternetSnackbar: Snackbar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)

        // Check if user is already logged in
        val tokenManager = TokenManager.getInstance(applicationContext)
        if (tokenManager.getToken() != null) {
            // User already logged in, go directly to MainActivity
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup network monitoring
        setupNetworkMonitoring()

        val email = binding.email
        val password = binding.password
        val login = binding.login
        val loading = binding.loading

        loginViewModel = ViewModelProvider(this, LoginViewModelFactory(TokenManager.getInstance(applicationContext)))[LoginViewModel::class.java]

        loginViewModel.loginFormState.observe(this@LoginActivity, Observer {
            val loginState = it ?: return@Observer

            // disable login button unless both email / password is valid
            login.isEnabled = loginState.isDataValid

            if (loginState.emailError != null) {
                email.error = getString(loginState.emailError)
            }
            if (loginState.passwordError != null) {
                password.error = getString(loginState.passwordError)
            }
        })

        loginViewModel.loginResult.observe(this@LoginActivity, Observer {
            val loginResult = it ?: return@Observer

            loading.visibility = View.GONE
            if (loginResult.error != null) {
                showLoginFailed(loginResult.error)
            }
            if (loginResult.success != null) {
                updateUiWithUser(loginResult.success)

                setResult(Activity.RESULT_OK)

                //Complete and destroy login activity once successful
                finish()
            }
        })

        email.afterTextChanged {
            loginViewModel.loginDataChanged(
                email.text.toString(),
                password.text.toString()
            )
        }

        password.apply {
            afterTextChanged {
                loginViewModel.loginDataChanged(
                    email.text.toString(),
                    password.text.toString()
                )
            }

            setOnEditorActionListener { _, actionId, _ ->
                when (actionId) {
                    EditorInfo.IME_ACTION_DONE ->
                        loginViewModel.login(
                            email.text.toString(),
                            password.text.toString()
                        )
                }
                false
            }

            login.setOnClickListener {
                loading.visibility = View.VISIBLE
                loginViewModel.login(email.text.toString(), password.text.toString())
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Only unregister if the property has been initialized
        if (::networkConnectivityManager.isInitialized) {
            networkConnectivityManager.unregisterNetworkCallback()
        }
    }

    private fun updateUiWithUser(model: LoggedInUserView) {
        val welcome = getString(R.string.welcome)
        val displayName = model.email
        Toast.makeText(
            applicationContext,
            "$welcome $displayName",
            Toast.LENGTH_LONG
        ).show()

        // Add flags to clear back stack
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }

    private fun showLoginFailed(@StringRes errorString: Int) {
        Toast.makeText(applicationContext, errorString, Toast.LENGTH_SHORT).show()
    }

    private fun setupNetworkMonitoring() {
        networkConnectivityManager = NetworkConnectivityManager(applicationContext)
        networkStatusViewModel = ViewModelProvider(this)[NetworkStatusViewModel::class.java]
        networkStatusViewModel.initialize(networkConnectivityManager)

        binding.root.post {
            networkStatusViewModel.isNetworkAvailable?.observe(this) { isAvailable ->
                if (isAvailable == true) {
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

/**
 * Extension function to simplify setting an afterTextChanged action to EditText components.
 */
fun EditText.afterTextChanged(afterTextChanged: (String) -> Unit) {
    this.addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(editable: Editable?) {
            afterTextChanged.invoke(editable.toString())
        }

        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
    })
}