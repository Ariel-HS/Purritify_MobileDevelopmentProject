package com.example.purritify.data.model

data class LoginResponse (
    val accessToken: String,
    val refreshToken: String,
    val error: String
)