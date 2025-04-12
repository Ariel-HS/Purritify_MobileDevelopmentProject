package com.example.purritify.data

import android.util.Log
import com.example.purritify.data.model.LoggedInUser
import com.example.purritify.data.model.LoginRequest
import com.example.purritify.data.model.LoginResponse
import com.example.purritify.retrofit.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Response
import java.io.IOException


/**
 * Class that handles authentication w/ login credentials and retrieves user information.
 */
class LoginDataSource {

    suspend fun login(email: String, password: String): Result<LoggedInUser> {
        return withContext(Dispatchers.IO) {
            return@withContext try {
                Log.d("LoginDataSource", "login called")

                val call: Call<LoginResponse> = RetrofitClient.getInstance().getMyApi()
                    .tryLogin(LoginRequest(email = email, password = password))

                val response: Response<LoginResponse> = call.execute()

                Log.d("LoginDataSource", "login finished")

                if (response.isSuccessful) {
                    Log.d("LoginDataSource", "Response success")
                    val loginResponse: LoginResponse = response.body()!!
                    val user = LoggedInUser(
                        email = email,
                        token = loginResponse.accessToken,
                        refreshToken = loginResponse.refreshToken
                    )
                    Result.Success(user)
                }
                else {
                    Log.d("LoginDataSource", "Response fail")
                    val loginResponse: LoginResponse? = response.body()
                    Result.Error(IOException("Error logging in ${loginResponse?.error}"))
                }

            } catch (e: Throwable) {
                return@withContext Result.Error(IOException("Error logging in", e))
            }
        }
    }
}