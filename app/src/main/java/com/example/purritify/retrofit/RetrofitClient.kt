package com.example.purritify.retrofit

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.logging.HttpLoggingInterceptor

class RetrofitClient {
    private var myApi: Api

    init {
        val client = OkHttpClient()

        val interceptor = HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY)
        val clientBuilder: OkHttpClient.Builder =
            client.newBuilder().addInterceptor(interceptor as HttpLoggingInterceptor)

        val retrofit: Retrofit = Retrofit.Builder()
            .baseUrl(Api.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(clientBuilder.build())
            .build()
        myApi = retrofit.create<Api>(Api::class.java)
    }

    companion object {
        @Volatile
        private var instance: RetrofitClient? = null

        @Synchronized
        fun getInstance(): RetrofitClient {
            if (instance == null) {
                instance = RetrofitClient()
            }
            return instance as RetrofitClient
        }
    }

    fun getMyApi(): Api {
        return myApi
    }
}