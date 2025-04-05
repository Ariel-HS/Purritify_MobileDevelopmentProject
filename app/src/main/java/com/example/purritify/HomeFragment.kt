package com.example.purritify

import android.content.Context
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
import com.example.purritify.data.room.Song
import com.example.purritify.data.room.SongDatabase
import com.example.purritify.ui.library.SongViewModel
import com.example.purritify.ui.library.SongViewModelFactory
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
    private var newSongs: List<Song> = emptyList()
    private var recentlyPlayedSongs: List<Song> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentHomeBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Load songs from database
        val context = requireContext().applicationContext

        getSongs(context)

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

        // toolbar.setNavigationIcon(R.drawable)
    }

    private fun getSongs(context: Context) {
        val density = resources.displayMetrics.density

        songDatabase = SongDatabase.getInstance(context)

        songViewModel = ViewModelProvider(
            this,
            SongViewModelFactory(songDatabase.songDao)
        )[SongViewModel::class.java]

        viewLifecycleOwner.lifecycleScope.launch {
            // Load new songs
            launch {
                songViewModel.newSongsState.collect { state ->
                    val songs = state.songs
                    if (songs is List<*> && songs.all {it is Song}) {
                        @Suppress("UNCHECKED_CAST")
                        newSongs = songs as List<Song>

                        val params = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            marginEnd = (18 * density).toInt()
                        }

                        loadSongsToContainer(newSongs, binding.newSongContainer, R.layout.song_card, params, maxCount = 10)
                    }
                }
            }

            // Load recently played
            launch {
                songViewModel.recentlyPlayedState.collect { state ->
                    val songs = state.songs
                    if (songs is List<*> && songs.all {it is Song}) {
                        @Suppress("UNCHECKED_CAST")
                        recentlyPlayedSongs = songs as List<Song>

                        val params = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )

                        loadSongsToContainer(recentlyPlayedSongs, binding.recentlyPlayedContainer, R.layout.song_item, params)
                    }

                }
            }
        }
    }

    private fun loadSongsToContainer(
        songs: List<Song>,
        container: LinearLayout,
        layoutResId: Int,
        layoutParams: ViewGroup.LayoutParams,
        maxCount: Int = Int.MAX_VALUE
    ) {
        val inflater = LayoutInflater.from(requireContext())
        container.removeAllViews()

        for (song in songs.take(maxCount)) {
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