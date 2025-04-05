package com.example.purritify.retrofit

import com.example.purritify.data.model.LoginRequest
import com.example.purritify.data.model.LoginResponse
import com.example.purritify.data.model.ProfileResponse
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.Header


interface Api {
    @POST("/api/login")
    fun tryLogin(
        @Body loginRequest: LoginRequest
    ): Call<LoginResponse>

    @GET("/api/profile")
    fun fetchProfile(
        @Header("Authorization") token: String
    ): Call<ProfileResponse>

    companion object {
        const val BASE_URL: String = "http://34.101.226.132:3000"
    }
}