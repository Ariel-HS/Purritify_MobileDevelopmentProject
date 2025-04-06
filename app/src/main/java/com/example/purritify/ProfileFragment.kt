package com.example.purritify

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
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
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.palette.graphics.Palette
import androidx.room.Room
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.example.purritify.data.room.SongDatabase
import com.example.purritify.databinding.FragmentProfileBinding
import com.example.purritify.retrofit.Api
import com.example.purritify.ui.library.SongViewModel
import com.example.purritify.ui.library.SongViewModelFactory
import com.example.purritify.ui.login.TokenManager
import com.example.purritify.ui.profile.ProfileViewModel
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * A simple [Fragment] subclass.
 * Use the [ProfileFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class ProfileFragment : Fragment() {
    private lateinit var binding: FragmentProfileBinding

    private lateinit var songDatabase: SongDatabase
    private lateinit var profileViewModel: ProfileViewModel
    private lateinit var songViewModel: SongViewModel

    private var liked: Int = 0
    private var listened: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Init database
        songDatabase = Room.databaseBuilder(
            requireContext(),
            SongDatabase::class.java,
            "songs.db"
        ).build()

        // Init ViewModels
        profileViewModel = ViewModelProvider(this)[ProfileViewModel::class.java]
        songViewModel = ViewModelProvider(
            this,
            SongViewModelFactory(songDatabase.songDao)
        )[SongViewModel::class.java]

        // Fetch profile from backend
        val token = TokenManager.getInstance(requireContext()).getToken()
        token?.let {
            profileViewModel.fetchProfile("Bearer $it")
        } ?: Log.d("ProfileFragment", "Token not found.")

        // Fetch statistics
        lifecycleScope.launch {
            // Liked
            songViewModel.getLikedSongCount().collect { count ->
                liked = count
            }

            // Listened
            songViewModel.getListenedSongCount().collect { count ->
                listened = count
            }

            // Songs
            // TODO
        }
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

        // Observe and update UI
        profileViewModel.profile.observe(viewLifecycleOwner, Observer { profile ->
            profile?.let {
                // Get profile picture
                val profileUrl = "${Api.BASE_URL}/uploads/profile-picture/${profile.profilePhoto}"

                loadProfileCard(profile.username, Locale("", profile.location).displayCountry, profileUrl)
                adjustBGColor(profileUrl)
                loadStatistics()
            }
        })
    }

    private fun loadProfileCard(name: String, location: String, profileUrl: String) {
        val container = binding.profileCardContainer

        val cardView = layoutInflater.inflate(R.layout.profile_card, container, false)

        val nameTextView = cardView.findViewById<TextView>(R.id.name)
        val locationTextView = cardView.findViewById<TextView>(R.id.location)
        val profileImageView = cardView.findViewById<ImageView>(R.id.profilePicture)

        // Set data
        nameTextView.text = name
        locationTextView.text = location
        Glide.with(this).
        load(profileUrl)
            .into(profileImageView)

        // Display card
        container.addView(cardView)
    }

    private fun loadStatistics() {
        // TODO: Songs

        // Load songs
        loadStatistic(binding.profileSongsStatistics, "000", "SONGS")

        // Load liked
        loadStatistic(binding.profileLikedStatistics, liked.toString(), "LIKED")

        // Load listened
        loadStatistic(binding.profileListenedStatistics, listened.toString(), "LISTENED")
    }

    private fun loadStatistic(container: FrameLayout, content: String, title: String) {
        val view = layoutInflater.inflate(R.layout.profile_statistics, container, false)

        val contentView = view.findViewById<TextView>(R.id.statisticsContent)
        val titleView = view.findViewById<TextView>(R.id.statisticsTitle)

        contentView.text = content
        titleView.text = title

        container.addView(view)
    }

    private fun adjustBGColor(profileUrl: String) {
        Glide.with(requireContext())
            .asBitmap()
            .load(profileUrl)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(bitmap: Bitmap, transition: Transition<in Bitmap>?) {
                    Palette.from(bitmap).generate { palette ->
                        // Get dominant color
                        val dominantColor = palette?.getDominantColor(Color.BLACK) ?: Color.BLACK

                        // Adjust brightness
                        val bgColor = ColorUtils.blendARGB(dominantColor, Color.BLACK, 0.3f)

                        // Create gradient
                        val gradientDrawable = GradientDrawable(
                            GradientDrawable.Orientation.TOP_BOTTOM,
                            intArrayOf(bgColor, Color.BLACK, Color.BLACK)
                        )

                        // Set as background color
                        binding.root.background = gradientDrawable
                    }
                }

                override fun onLoadCleared(placeholder: Drawable?) { }
            })
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @return A new instance of fragment ProfileFragment.
         */
        @JvmStatic
        fun newInstance() = ProfileFragment()
    }
}