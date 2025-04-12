package com.example.purritify

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.example.purritify.databinding.FragmentHomeBinding
import android.widget.ImageView
import android.widget.TextView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.bumptech.glide.Glide
import com.example.purritify.data.room.Song
import com.example.purritify.data.room.SongDatabase
import com.example.purritify.ui.library.SongViewModel
import com.example.purritify.ui.library.SongViewModelFactory
import com.example.purritify.ui.login.LoginActivity
import com.example.purritify.ui.login.TokenManager
import com.example.purritify.ui.player.MiniplayerView
import com.example.purritify.ui.player.PlayerViewModel
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private lateinit var binding: FragmentHomeBinding

    private lateinit var songDatabase: SongDatabase

    private lateinit var songViewModel: SongViewModel
    private lateinit var playerViewModel: PlayerViewModel

    private var newSongs: List<Song> = emptyList()
    private var recentlyPlayedSongs: List<Song> = emptyList()

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
        miniPlayer.activateMiniplayer(viewLifecycleOwner, playerViewModel, songViewModel) {
            val action = HomeFragmentDirections.actionHomeToPlayer(songId = it.id, true)
            findNavController().navigate(action)
        }

        // Logout
        binding.homeToolbar.logoutButton.setOnClickListener {
            logout()
        }


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
}