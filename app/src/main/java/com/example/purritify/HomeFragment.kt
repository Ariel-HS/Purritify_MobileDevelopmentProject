package com.example.purritify

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.room.Room
import com.bumptech.glide.Glide
import com.example.purritify.data.model.PlayableSong
import com.example.purritify.data.room.Song
import com.example.purritify.data.room.SongDatabase
import com.example.purritify.databinding.FragmentHomeBinding
import com.example.purritify.ui.library.SongViewModel
import com.example.purritify.ui.library.SongViewModelFactory
import com.example.purritify.ui.login.LoginActivity
import com.example.purritify.ui.login.TokenManager
import com.example.purritify.ui.player.MiniplayerView
import com.example.purritify.ui.player.PlayerViewModel
import kotlinx.coroutines.launch
import androidx.core.graphics.drawable.toDrawable

class HomeFragment : Fragment() {

    private lateinit var binding: FragmentHomeBinding

    private lateinit var songDatabase: SongDatabase

    private lateinit var songViewModel: SongViewModel
    private lateinit var playerViewModel: PlayerViewModel

    private var newSongs: List<Song> = emptyList()
    private var recentlyPlayedSongs: List<Song> = emptyList()
    private var allSongs: List<PlayableSong> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val userEmail = TokenManager.getInstance(requireContext()).getUserEmail()

        if (userEmail.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "Invalid user email access", Toast.LENGTH_SHORT).show()
            Log.d("HomeFragment", "Access while not logged in")

            return
        }

        // Init db
        songDatabase = Room.databaseBuilder(
            requireContext(),
            SongDatabase::class.java,
            "songs.db"
        ).build()

        //  Init viewmodel
        songViewModel = ViewModelProvider(
            this,
            SongViewModelFactory(songDatabase.songDao, userEmail)
        )[SongViewModel::class.java]

        playerViewModel = ViewModelProvider(requireActivity())[PlayerViewModel::class.java]

        // Get songs
        viewLifecycleOwner.lifecycleScope.launch {
            launch {
                songViewModel.getAllSongs().collect { songs ->
                    allSongs = songs
                }
            }

            launch {
                songViewModel.newSongsState.collect { state ->
                    val songs = state.songs
                    if (songs is List<*> && songs.all {it is Song}) {
                        @Suppress("UNCHECKED_CAST")
                        newSongs = songs as List<Song>

                        loadNewSongs()
                    }
                }
            }

            launch {
                songViewModel.recentlyPlayedState.collect { state ->
                    val songs = state.songs
                    if (songs is List<*> && songs.all {it is Song}) {
                        @Suppress("UNCHECKED_CAST")
                        recentlyPlayedSongs = songs as List<Song>

                        loadRecentlyPlayed()
                    }

                }
            }
        }

        // Miniplayer logic
        val miniPlayer = view.findViewById<MiniplayerView>(R.id.miniPlayer)
        miniPlayer.activateMiniplayer(viewLifecycleOwner, playerViewModel, songViewModel, childFragmentManager) {
            val action = HomeFragmentDirections.actionHomeToPlayer(songId = it.id, fromMiniplayer = true)
            findNavController().navigate(action)
        }

        setupOnClickListener()
    }

    private fun logout() {
        val tokenManager = TokenManager.getInstance(requireContext())

        // Clear tokens
        Log.d("HomeFragment", "Logging out, clearing tokens")
        tokenManager.clearUserEmail()
        tokenManager.clearToken()
        tokenManager.clearRefreshToken()
        Log.d("HomeFragment", "Access Token: ${tokenManager.getToken()}")
        Log.d("HomeFragment", "Refresh Token: ${tokenManager.getRefreshToken()}")
        Log.d("HomeFragment", "User Email: ${tokenManager.getUserEmail()}")

        // Stop token refresh service
        (activity as? MainActivity)?.stopTokenRefreshService()

        // Navigate to login
        val intent = Intent(requireContext(), LoginActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        requireActivity().finish()
    }

    private fun loadNewSongs() {
        val density = resources.displayMetrics.density

        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            marginEnd = (18 * density).toInt()
        }

        loadSongsToContainer(newSongs, binding.newSongContainer, R.layout.song_card, params)
    }

    private fun loadRecentlyPlayed() {
        val density = resources.displayMetrics.density

        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = (12 * density).toInt()
        }

        loadSongsToContainer(recentlyPlayedSongs, binding.recentlyPlayedContainer, R.layout.home_song_card, params)
    }

    private fun loadSongsToContainer(
        songs: List<Song>,
        container: LinearLayout,
        layoutResId: Int,
        layoutParams: ViewGroup.LayoutParams,
    ) {
        val inflater = LayoutInflater.from(requireContext())
        container.removeAllViews()

        for (song in songs) {
            val songView = inflater.inflate(layoutResId, container, false)

            val cover = songView.findViewById<ImageView>(R.id.songArtwork)
            val title = songView.findViewById<TextView>(R.id.songTitle)
            val artist = songView.findViewById<TextView>(R.id.songArtist)

            // Set data
            title.text = song.title
            artist.text = song.artist
            loadCover(song.pathArtwork, cover)

            // Redirect to player fragment
            songView.setOnClickListener {
                val action = HomeFragmentDirections.actionHomeToPlayer(
                    songId = song.id,
                )
                findNavController().navigate(action)
            }

            // Display containers
            songView.layoutParams = layoutParams
            container.addView(songView)
        }
    }

    private fun loadCover(coverPath: String, coverView: ImageView) {
        try {
            Glide.with(requireContext())
                .load(coverPath.toUri())
                .into(coverView)
        } catch (_: Exception) {
            Log.d("HomeFragment", "Cover doesn't exist at $coverPath, defaulting to sample cover")

            coverView.setImageResource(R.drawable.sample_cover)
        }
    }

    private fun setupOnClickListener() {
        // Logout
        binding.homeToolbar.logoutButton.setOnClickListener {
            logout()
        }

        binding.globalChart.setOnClickListener {
            val action = HomeFragmentDirections.actionHomeToChart(chartType = "global")
            findNavController().navigate(action)
        }

        binding.localChart.setOnClickListener {
            val action = HomeFragmentDirections.actionHomeToChart(chartType = "local")
            findNavController().navigate(action)
        }

        // Hamburger
        binding.homeToolbar.hamburgerButton.setOnClickListener { showHamburgerDropdown(it) }
    }

    @SuppressLint("InflateParams")
    private fun showHamburgerDropdown(anchorView: View) {
        val inflater = LayoutInflater.from(requireContext())
        val dropdownView = inflater.inflate(R.layout.dropdown_menu, null)

        val popupWindow = PopupWindow(
            dropdownView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true // focusable
        )

        popupWindow.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        popupWindow.elevation = 8f

        val scanQr = dropdownView.findViewById<TextView>(R.id.scanQr)
        scanQr.setOnClickListener {
            val activity = context as? Activity ?: return@setOnClickListener

            handleQrScanner(activity, popupWindow)
        }

        popupWindow.showAsDropDown(anchorView)
    }

    private fun handleQrScanner(activity: Activity, popupWindow: PopupWindow) {
        if (ContextCompat.checkSelfPermission(activity, android.Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            popupWindow.dismiss()

            // Permission already granted
            openQrScanner(activity)
        } else {
            // Request camera permission
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(android.Manifest.permission.CAMERA),
                REQUEST_CODE_CAMERA_PERMISSION
            )
        }
    }

    private fun openQrScanner(activity: Activity) {
        val intent = Intent(activity, com.journeyapps.barcodescanner.CaptureActivity::class.java)
        intent.putExtra("SCAN_MODE", "QR_CODE_MODE")
        activity.startActivityForResult(intent, REQUEST_CODE_QR_SCAN)
    }

    companion object {
        private const val REQUEST_CODE_QR_SCAN = 101
        const val REQUEST_CODE_CAMERA_PERMISSION = 1002
    }
}