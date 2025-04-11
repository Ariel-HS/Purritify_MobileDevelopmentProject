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
import com.example.purritify.network.NetworkStatusViewModel
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
    private lateinit var playerViewModel: PlayerViewModel
    private lateinit var networkStatusViewModel: NetworkStatusViewModel

    private var songs: Int = 0
    private var liked: Int = 0
    private var listened: Int = 0

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

        // Initialize network status viewmodel
        networkStatusViewModel = ViewModelProvider(requireActivity())[NetworkStatusViewModel::class.java]
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

        // Profile
        profileViewModel.profile.observe(viewLifecycleOwner, Observer { profile ->
            profile?.let {
                // Get profile picture
                val profileUrl = "${Api.BASE_URL}/uploads/profile-picture/${profile.profilePhoto}"

                loadProfileCard(profile.username, Locale("", profile.location).displayCountry, profileUrl)
                adjustBGColor(profileUrl)
            }
        })

        // Setup network status monitoring
        setupNetworkStatusObserver()

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
            val action = ProfileFragmentDirections.actionProfileToPlayer(songId = it.id)
            findNavController().navigate(action)
        }
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

    private fun setupNetworkStatusObserver() {
        networkStatusViewModel.isNetworkAvailable?.observe(viewLifecycleOwner) { isAvailable ->
            if (isAvailable) {
                // Internet available, show profile data and fetch profile
                binding.profileCardContainer.visibility = View.VISIBLE
                binding.root.findViewById<View>(R.id.profile_offline_message)?.visibility = View.GONE

                // Fetch profile only when internet is available
                val token = TokenManager.getInstance(requireContext()).getToken()
                token?.let {
                    profileViewModel.fetchProfile("Bearer $it")
                } ?: Log.d("ProfileFragment", "Token not found.")

                // Observe profile data
                profileViewModel.profile.observe(viewLifecycleOwner) { profile ->
                    profile?.let {
                        // Get profile picture
                        val profileUrl = "${Api.BASE_URL}/uploads/profile-picture/${profile.profilePhoto}"

                        loadProfileCard(profile.username, Locale("", profile.location).displayCountry, profileUrl)
                        adjustBGColor(profileUrl)
                    }
                }
            } else {
                // No internet, show offline message
                binding.profileCardContainer.visibility = View.GONE

                // Check if offline message view exists, if not create it
                if (binding.root.findViewById<View>(R.id.profile_offline_message) == null) {
                    val offlineMessage = TextView(requireContext()).apply {
                        id = R.id.profile_offline_message
                        text = "Profile information requires internet connection"
                        setTextColor(Color.WHITE)
                        textSize = 16f
                        gravity = Gravity.CENTER
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        setPadding(32, 100, 32, 32)
                    }
                    binding.root.addView(offlineMessage)
                } else {
                    binding.root.findViewById<View>(R.id.profile_offline_message).visibility = View.VISIBLE
                }
            }
        }
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