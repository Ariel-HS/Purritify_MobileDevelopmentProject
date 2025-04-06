package com.example.purritify.ui.profile

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.purritify.data.model.ProfileResponse
import com.example.purritify.retrofit.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response


class ProfileViewModel : ViewModel() {
    private val _profile = MutableLiveData<ProfileResponse>()
    val profile: LiveData<ProfileResponse> get() = _profile

    fun fetchProfile(token: String) {
        val api = RetrofitClient.getInstance().getMyApi()
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
}