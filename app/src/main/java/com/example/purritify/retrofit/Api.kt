package com.example.purritify.retrofit

import com.example.purritify.data.model.ChartSong
import com.example.purritify.data.model.LoginRequest
import com.example.purritify.data.model.LoginResponse
import com.example.purritify.data.model.MessageResponse
import com.example.purritify.data.model.ProfileResponse
import com.example.purritify.data.model.RefreshTokenRequest
import com.example.purritify.data.model.RefreshTokenResponse
import com.example.purritify.data.model.TokenVerifyResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.Part
import retrofit2.http.Path


interface Api {
    @POST("/api/login")
    fun tryLogin(
        @Body loginRequest: LoginRequest
    ): Call<LoginResponse>

    @GET("/api/profile")
    fun fetchProfile(
        @Header("Authorization") token: String
    ): Call<ProfileResponse>

    @GET("/api/profile")
    suspend fun fetchProfileSuspend(
        @Header("Authorization") token: String
    ): ProfileResponse

    @GET("api/verify-token")
    suspend fun verifyToken(
        @Header("Authorization") authHeader: String
    ): Response<TokenVerifyResponse>

    @POST("api/refresh-token")
    suspend fun refreshToken(
        @Body refreshTokenRequest: RefreshTokenRequest
    ): Response<RefreshTokenResponse>

    @GET("/api/top-songs/global")
    suspend fun fetchGlobalSongs(

    ): List<ChartSong>

    @GET("/api/top-songs/{country-code}")
    suspend fun fetchLocalSongs(
        @Path(value = "country-code", encoded = true) countryCode: String
    ): List<ChartSong>

    @Multipart
    @PATCH("/api/profile")
    suspend fun editProfile(
        @Header("Authorization") token: String,
        @Part("location") location: RequestBody?,
        @Part profilePhoto: MultipartBody.Part?
    ): Response<MessageResponse>

    companion object {
        const val BASE_URL: String = "http://34.101.226.132:3000"
    }
}