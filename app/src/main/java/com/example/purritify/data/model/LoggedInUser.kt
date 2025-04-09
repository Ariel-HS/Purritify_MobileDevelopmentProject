package com.example.purritify.data.model

/**
 * Data class that captures user information for logged in users retrieved from LoginRepository
 */
data class LoggedInUser(
    val email: String,
    val token: String,
    val refreshToken: String,
)