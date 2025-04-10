package com.example.purritify.data.model

data class RefreshTokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val error: String? = null
)
