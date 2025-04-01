package com.example.purritify.retrofit

import com.example.purritify.data.model.LoginRequest
import com.example.purritify.data.model.LoginResponse
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST


interface Api {
    @POST("/api/login")
    fun tryLogin(
        @Body loginRequest: LoginRequest
    ): Call<LoginResponse>

    companion object {
        const val BASE_URL: String = "http://34.101.226.132:3000"
    }
}