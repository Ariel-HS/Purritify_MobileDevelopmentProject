package com.example.purritify.ui.profile

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.purritify.data.model.MessageResponse
import com.example.purritify.data.model.ProfileResponse
import com.example.purritify.retrofit.RetrofitClient
import kotlinx.coroutines.launch
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response


class ProfileViewModel : ViewModel() {
    private val _profile = MutableLiveData<ProfileResponse>()
    val profile: LiveData<ProfileResponse> get() = _profile

    private val api = RetrofitClient.getInstance().getMyApi()

    fun fetchProfile(token: String) {
        api.fetchProfile(token).enqueue(object : Callback<ProfileResponse> {
            override fun onResponse(call: Call<ProfileResponse>, response: Response<ProfileResponse>) {
                if (response.isSuccessful)
                    _profile.value = response.body()
                else
                    Log.e("ProfileViewModel", "Error: ${response.code()} - ${response.message()}")
            }

            override fun onFailure(call: Call<ProfileResponse>, t: Throwable) {
                Log.e("ProfileViewModel", "API failure: ${t.message}", t)
            }
        })
    }

    fun updateProfile(token: String, location: RequestBody?, profilePhoto: MultipartBody.Part?, onSuccess: (MessageResponse) -> Unit, onError: (Throwable) -> Unit) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.getInstance().getMyApi().editProfile(token, location, profilePhoto)
                if (response.isSuccessful) {
                    response.body()?.let { onSuccess(it) }
                } else {
                    onError(Throwable("Error: ${response.code()}"))
                }
            } catch (e: Exception) {
                onError(e)
            }
        }
    }
}