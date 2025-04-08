package com.example.purritify

import android.os.Bundle
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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.example.purritify.data.room.Song
import com.example.purritify.data.room.SongDatabase
import com.example.purritify.ui.library.SongViewModel
import com.example.purritify.ui.library.SongViewModelFactory
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

        // Init db
        songDatabase = Room.databaseBuilder(
            requireContext(),
            SongDatabase::class.java,
            "songs.db"
        ).build()

        //  Init viewmodel
        songViewModel = ViewModelProvider(
            this,
            SongViewModelFactory(songDatabase.songDao)
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
        playerViewModel.currentSong.observe(viewLifecycleOwner) { song ->
            val container = binding.miniPlayerContainer

            if (song != null) {
                container.removeAllViews()

                // Inflate the miniplayer layout
                val miniPlayerView = layoutInflater.inflate(R.layout.miniplayer, container, false)
                container.addView(miniPlayerView)
                container.visibility = View.VISIBLE

                // Fill in song data
                miniPlayerView.findViewById<TextView>(R.id.songTitle).text = song.title
                miniPlayerView.findViewById<TextView>(R.id.songArtist).text = song.artist

                // TODO: Cover, pause button, add button, bg color, redirect to player
            } else {
                container.removeAllViews()
                container.visibility = View.GONE
            }
        }


        // Toolbar & Navigation
        purritifyToolbar()

        val navController = findNavController()
        binding.homeToolbar.setupWithNavController(navController)

        val drawerLayout = binding.homeDrawerLayout
        val toggle = ActionBarDrawerToggle(requireActivity(), drawerLayout, binding.homeToolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        binding.homeNavView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.profile -> {
                    // TODO("handle profile")
                }

                R.id.home_sidebar_settings -> {
                    // TODO("handle settings")
                }
            }

            drawerLayout.closeDrawers()
            true
        }
    }

    private fun purritifyToolbar() {
        val toolbar = binding.homeToolbar

//         toolbar.setNavigationIcon(R.drawable)
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

        loadSongsToContainer(recentlyPlayedSongs, binding.recentlyPlayedContainer, R.layout.library_song_card, params)
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