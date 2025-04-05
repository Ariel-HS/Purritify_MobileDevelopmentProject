package com.example.purritify

import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import com.example.purritify.data.model.ProfileResponse
import com.example.purritify.databinding.FragmentProfileBinding
import com.example.purritify.retrofit.RetrofitClient
import com.example.purritify.ui.login.TokenManager
import retrofit2.Callback
import retrofit2.Call
import retrofit2.Response
import java.util.Locale

/**
 * A simple [Fragment] subclass.
 * Use the [ProfileFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class ProfileFragment : Fragment() {
    private lateinit var binding: FragmentProfileBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        binding = FragmentProfileBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Fetch profile from backend
        val token = TokenManager.getInstance(requireContext()).getToken()

        if (token != null)
            fetchProfile("Bearer $token")
        else
            Log.d("ProfileFragment", "Token not found.")

    }

    private fun fetchProfile(token: String) {
        val api = RetrofitClient.getInstance().getMyApi()
        api.fetchProfile(token).enqueue(object: Callback<ProfileResponse> {
            override fun onResponse(call: Call<ProfileResponse>, response: Response<ProfileResponse>) {
                if (response.isSuccessful) {
                    val profile = response.body()

                    profile?.let {
                        loadProfileCard(profile.username, Locale("", profile.location).displayCountry, profile.profilePhoto)
                        adjustBGColor(profile.profilePhoto)
                        loadStatistics()
                    }
                } else
                    Log.e("ProfileFragment", "Error: ${response.code()} - ${response.message()}")
            }

            override fun onFailure(call: Call<ProfileResponse>, t: Throwable) {
                Log.e("ProfileFragment", "API failure: ${t.message}", t)
            }
        })
    }

    private fun loadProfileCard(name: String, location: String, profilePhoto: String) {
        val container = binding.profileCardContainer

        val cardView = layoutInflater.inflate(R.layout.profile_card, container, false)

        val nameTextView = cardView.findViewById<TextView>(R.id.name)
        val locationTextView = cardView.findViewById<TextView>(R.id.location)
        val profileImageView = cardView.findViewById<ImageView>(R.id.profilePicture)

        // Set data
        nameTextView.text = name
        locationTextView.text = location
        profileImageView.setImageResource(R.drawable.sample_profile) // TODO: Implement profile pictures

        // Display card
        container.addView(cardView)
    }

    private fun loadStatistics() {
        // TODO: Dynamically assign content

        // Load songs
        loadStatistic(binding.profileSongsStatistics, "000", "SONGS")

        // Load liked
        loadStatistic(binding.profileLikedStatistics, "001", "LIKED")

        // Load listened
        loadStatistic(binding.profileListenedStatistics, "002", "LISTENED")
    }

    private fun loadStatistic(container: FrameLayout, content: String, title: String) {
        val view = layoutInflater.inflate(R.layout.profile_statistics, container, false)

        val contentView = view.findViewById<TextView>(R.id.statisticsContent)
        val titleView = view.findViewById<TextView>(R.id.statisticsTitle)

        contentView.text = content
        titleView.text = title

        container.addView(view)
    }

    private fun adjustBGColor(profilePhoto: String) {
        val drawable = ContextCompat.getDrawable(requireContext(), R.drawable.sample_profile) as BitmapDrawable // TODO: Implement profile pictures
        val bitmap = drawable.bitmap

        Palette.from(bitmap).generate { palette ->
            val dominantColor = palette?.getDominantColor(Color.BLACK) ?: Color.BLACK

            val bgColor = ColorUtils.blendARGB(dominantColor, Color.BLACK, 0.3f)

            val gradientDrawable = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(bgColor, Color.BLACK, Color.BLACK)
            )

            binding.root.background = gradientDrawable
        }
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment ProfileFragment.
         */
        @JvmStatic
        fun newInstance() = ProfileFragment()
    }
}