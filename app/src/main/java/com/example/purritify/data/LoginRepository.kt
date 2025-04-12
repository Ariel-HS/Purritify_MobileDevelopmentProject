package com.example.purritify.data

import android.util.Log
import com.example.purritify.data.model.LoggedInUser
import com.example.purritify.ui.login.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Class that requests authentication and user information from the remote data source and
 * maintains an in-memory cache of login status and user credentials information.
 */

class LoginRepository(val dataSource: LoginDataSource, private val tokenManager: TokenManager) {

    // in-memory cache of the loggedInUser object
    var user: LoggedInUser? = null
        private set

    suspend fun login(username: String, password: String): Result<LoggedInUser> {
        return withContext(Dispatchers.IO) {
            // handle login
            Log.d("LoginRepository", "attempt login")
            val result = dataSource.login(username, password)

            if (result is Result.Success) {
                setLoggedInUser(result.data)

                Log.d("LoginRepository", "Logging in, saving tokens")
                tokenManager.saveToken(result.data.token)
                tokenManager.saveRefreshToken(result.data.refreshToken)
                tokenManager.saveUserEmail(result.data.email)
                Log.d("LoginRepository", "Saving tokens finished")
                Log.d("LoginRepository", "Access Token: ${tokenManager.getToken()}")
                Log.d("LoginRepository", "Refresh Token: ${tokenManager.getRefreshToken()}")
                Log.d("LoginRepository", "User Email: ${tokenManager.getUserEmail()}")
            }

            return@withContext result
        }
    }

    private fun setLoggedInUser(loggedInUser: LoggedInUser) {
        this.user = loggedInUser
    }
}