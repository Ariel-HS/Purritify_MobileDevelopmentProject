package com.example.purritify.ui.login

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class TokenManager private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("auth", Context.MODE_PRIVATE)

    fun saveToken(token: String) { prefs.edit { putString("access_token", token) } }

    fun getToken(): String? { return prefs.getString("access_token", null) }

    fun saveRefreshToken(token: String) { prefs.edit { putString("refresh_token", token) } }

    fun getRefreshToken(): String? { return prefs.getString("refresh_token", null) }

    fun saveUserEmail(email: String) { prefs.edit { putString("user_email", email) } }

    fun getUserEmail(): String? { return prefs.getString("user_email", null) }

    fun clearToken() { prefs.edit { remove("access_token") } }

    fun clearRefreshToken() { prefs.edit { remove("refresh_token") } }

    fun clearUserEmail() { prefs.edit { remove("user_email") } }

    companion object {
        @Volatile
        private var INSTANCE: TokenManager? = null

        fun getInstance(context: Context): TokenManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TokenManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}