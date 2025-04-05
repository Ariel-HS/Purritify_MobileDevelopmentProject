package com.example.purritify.ui.login

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class TokenManager private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("auth", Context.MODE_PRIVATE)

    fun saveToken(token: String) { prefs.edit { putString("access_token", token) } }

    fun getToken(): String? { return prefs.getString("access_token", null) }

    fun clearToken() { prefs.edit { remove("access_token") } }

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