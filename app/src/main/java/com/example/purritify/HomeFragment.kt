package com.example.purritify

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import com.example.purritify.databinding.FragmentHomeBinding
import android.widget.ImageView
import android.widget.TextView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.example.purritify.data.room.Song
import com.example.purritify.data.room.SongDatabase
import com.example.purritify.ui.library.SongViewModel
import com.example.purritify.ui.library.SongViewModelFactory
import com.example.purritify.ui.login.LoginActivity
import com.example.purritify.ui.login.TokenManager
import com.example.purritify.ui.player.MiniplayerView
import com.example.purritify.ui.player.PlayerViewModel
import kotlinx.coroutines.launch

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [HomeFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
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
        miniPlayer.activateMiniplayer(viewLifecycleOwner, playerViewModel, songViewModel)

        binding.homeToolbar.logoutButton.setOnClickListener {
            TokenManager.getInstance(requireContext()).clearUserEmail()
            TokenManager.getInstance(requireContext()).clearToken()

            startActivity(Intent(requireContext(), LoginActivity::class.java))
            requireActivity().finish()
        }


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
        )

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
            cover.setImageResource(R.drawable.sample_cover) // TODO: Sample cover

            // Display containers
            songView.layoutParams = layoutParams
            container.addView(songView)
        }
    }
}