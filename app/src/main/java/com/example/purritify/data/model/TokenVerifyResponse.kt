package com.example.purritify.data.model

data class TokenVerifyResponse(
    val valid: Boolean,
    val user: UserInfo?,
    val error: String?
)
