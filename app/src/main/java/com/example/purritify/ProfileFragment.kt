package com.example.purritify

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
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
import com.example.purritify.ui.player.MiniplayerView
import com.example.purritify.ui.player.PlayerViewModel
import com.example.purritify.ui.profile.ProfileViewModel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.core.graphics.toColorInt
import com.example.purritify.ui.profile.EditProfileDialog
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

class ProfileFragment : Fragment() {
    private lateinit var binding: FragmentProfileBinding

    private lateinit var songDatabase: SongDatabase
    private lateinit var profileViewModel: ProfileViewModel
    private lateinit var songViewModel: SongViewModel
    private lateinit var playerViewModel: PlayerViewModel

    private var songs: Int = 0
    private var liked: Int = 0
    private var listened: Int = 0

    private lateinit var location: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val userEmail = TokenManager.getInstance(requireContext()).getUserEmail()

        if (userEmail.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "Invalid user email access", Toast.LENGTH_SHORT).show()
            Log.d("ProfileFragment", "Access while not logged in")

            return
        }

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
            SongViewModelFactory(songDatabase.songDao, userEmail)
        )[SongViewModel::class.java]

        // Fetch profile from backend
        val token = TokenManager.getInstance(requireContext()).getToken()
        token?.let {
            profileViewModel.fetchProfile("Bearer $it")
        } ?: Log.d("ProfileFragment", "Token not found.")

        playerViewModel = ViewModelProvider(requireActivity())[PlayerViewModel::class.java]
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

        // Setup network monitoring
        setupNetworkMonitoring()

        // Profile
        profileViewModel.profile.observe(viewLifecycleOwner, Observer { profile ->
            profile?.let {
                // Get profile picture
                val profileUrl = "${Api.BASE_URL}/uploads/profile-picture/${profile.profilePhoto}"

                // Set location
                location = profile.location

                loadProfileCard(profile.username, Locale("", profile.location).displayCountry, profileUrl)
                adjustBGColor(profileUrl)
            }
        })

        // Statistics
        lifecycleScope.launch {
            // Fetch data
            val songCountDeferred: Deferred<Int> = async { songViewModel.getSongCount().first() }
            val likedCountDeferred: Deferred<Int> = async { songViewModel.getLikedSongCount().first() }
            val listenedCountDeferred: Deferred<Int> = async { songViewModel.getListenedSongCount().first() }

            songs = songCountDeferred.await()
            liked = likedCountDeferred.await()
            listened = listenedCountDeferred.await()

            // Display data
            loadStatistics()
        }

        // Miniplayer logic
        val miniPlayer = view.findViewById<MiniplayerView>(R.id.miniPlayer)
        miniPlayer.activateMiniplayer(viewLifecycleOwner, playerViewModel, songViewModel) {
            val action = ProfileFragmentDirections.actionProfileToPlayer(songId = it.id, true)
            findNavController().navigate(action)
        }
    }

    private fun loadProfileCard(name: String, location: String, profileUrl: String) {
        val container = binding.profileCardContainer

        val cardView = layoutInflater.inflate(R.layout.profile_card, container, false)

        val nameTextView = cardView.findViewById<TextView>(R.id.name)
        val locationTextView = cardView.findViewById<TextView>(R.id.location)
        val profileImageView = cardView.findViewById<ImageView>(R.id.profilePicture)

        val miniEditProfileButton = cardView.findViewById<ImageButton>(R.id.editButton)
        setupEditProfile(binding.editProfileButton, miniEditProfileButton)

        // Set data
        nameTextView.text = name
        locationTextView.text = location
        Glide.with(this).
        load(profileUrl)
            .into(profileImageView)

        // Display card
        container.removeAllViews()
        container.addView(cardView)
    }

    @SuppressLint("MemberExtensionConflict")
    private fun loadStatistics() {
        // Load songs
        loadStatistic(binding.profileSongsStatistics, songs.toString(), "SONGS")

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

    @SuppressLint("SetTextI18n")
    private fun setupNetworkMonitoring() {
        // Get reference to the NetworkStatusViewModel
        val networkStatusViewModel = (requireActivity() as MainActivity).getNetworkStatusViewModel()

        // Create offline message if it doesn't exist
        if (binding.root.findViewById<TextView>(R.id.profile_offline_message) == null) {
            val offlineMessage = TextView(requireContext()).apply {
                id = R.id.profile_offline_message
                text = "No internet connection. Profile data may not be up to date."
                setBackgroundColor("#AA444444".toColorInt())
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(resources.getDimensionPixelSize(R.dimen.view_padding), resources.getDimensionPixelSize(R.dimen.view_padding), resources.getDimensionPixelSize(R.dimen.view_padding), resources.getDimensionPixelSize(R.dimen.view_padding))
                visibility = View.GONE
            }

            // Add to the top of the layout
            val params = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.gravity = Gravity.TOP
            binding.root.addView(offlineMessage, params)
        }

        // Observe network status
        networkStatusViewModel.isNetworkAvailable?.observe(viewLifecycleOwner) { isAvailable ->
            val offlineMessage = binding.root.findViewById<TextView>(R.id.profile_offline_message)
            offlineMessage?.visibility = if (isAvailable == true) View.GONE else View.VISIBLE

            // If network becomes available, refresh profile data
            if (isAvailable == true) {
                val token = TokenManager.getInstance(requireContext()).getToken()
                token?.let {
                    Log.d("ProfileFragment", "Network reconnected, refreshing profile data")
                    profileViewModel.fetchProfile("Bearer $it")
                }
            }
        }
    }

    @SuppressLint("MemberExtensionConflict")
    private fun setupEditProfile(vararg buttons: View) {
        buttons.forEach { button ->
            button.setOnClickListener {
                EditProfileDialog(
                    onSubmit = { location, profilePhoto ->
                        val token = TokenManager.getInstance(requireContext()).getToken()
                        if (token != null) {
                            profileViewModel.updateProfile(
                                "Berar $token",
                                location?.toRequestBody("text/plain".toMediaTypeOrNull()),
                                profilePhoto,
                                onSuccess = {
                                    Toast.makeText(requireContext(), "Profile updated!", Toast.LENGTH_SHORT).show()
                                    profileViewModel.fetchProfile("Bearer $token")
                                },
                                onError = {
                                    Toast.makeText(requireContext(), "Update failed: $it", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                ).show(childFragmentManager, "EditProfileDialog")
            }
        }
    }
}