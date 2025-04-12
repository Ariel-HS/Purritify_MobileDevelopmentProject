package com.example.purritify

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.purritify.databinding.FragmentPlayerBinding
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.room.Room
import com.example.purritify.data.room.Song
import com.example.purritify.data.room.SongDatabase
import com.example.purritify.ui.library.SongViewModel
import com.example.purritify.ui.library.SongViewModelFactory
import com.example.purritify.ui.player.PlayerViewModel
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.core.net.toUri
import androidx.navigation.fragment.navArgs
import com.example.purritify.ui.login.TokenManager
import com.example.purritify.ui.player.animatePauseIcon
import com.example.purritify.ui.player.handleCover
import com.example.purritify.ui.player.handleLike

enum class Direction {
    NEXT, PREVIOUS
}

class PlayerFragment : Fragment() {
    private val args: PlayerFragmentArgs by navArgs()
    private var songId: Int = -1
    private var currentSong: Song? = null
    private var songList: List<Song> = emptyList()

    private var userEmail: String? = null

    private lateinit var binding: FragmentPlayerBinding
    private lateinit var playerViewModel: PlayerViewModel

    private lateinit var songViewModel: SongViewModel

    private lateinit var songDatabase: SongDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get arguments
        songId = args.songId

        // Fetch email
        userEmail = TokenManager.getInstance(requireContext()).getUserEmail()

        if (userEmail.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "Invalid user email access", Toast.LENGTH_SHORT).show()

            return
        }

        // Init database
        songDatabase = Room.databaseBuilder(
            requireContext(),
            SongDatabase::class.java,
            "songs.db"
        ).build()

        // Init ViewModels
        playerViewModel = ViewModelProvider(requireActivity())[PlayerViewModel::class.java]
        songViewModel = ViewModelProvider(
            this,
            SongViewModelFactory(songDatabase.songDao, userEmail!!)
        )[SongViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentPlayerBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Handle close button
        binding.closeButton.setOnClickListener { findNavController().popBackStack() }

        // Get song
        viewLifecycleOwner.lifecycleScope.launch {
            songViewModel.getAllSongs().collect { songs ->
                songList = songs
                val song = songs.firstOrNull { it.id == songId }

                if (song == null) {
                    Log.d("PlayerFragment", "No song found with id $songId, going back to home page" )

                    findNavController().popBackStack()
                    return@collect
                }

                currentSong = song

                // Update UI
                handleData()

                // Handle playback state
                val currentlyPlaying = playerViewModel.currentSong.value
                if (!playerViewModel.isPlaying() && playerViewModel.isPaused.value != true) playSong()                                          // Start player if not currently playing & not currently paused
                else if (playerViewModel.isPaused.value == true && currentlyPlaying?.id == currentSong?.id) {                                   // Resume playing if the same song is clicked and it's currently paused
                    Handler(Looper.getMainLooper()).postDelayed({
                        playerViewModel.togglePause()
                    }, 200)
                }
                else if ((playerViewModel.isPlaying() || playerViewModel.isPaused.value == true) && currentlyPlaying?.id != currentSong?.id) {  // Restart player if songId is different than currentSong
                    Handler(Looper.getMainLooper()).postDelayed({
                        playSong()
                    }, 200)
                }

                // Handle like button
                binding.heartButton.setOnClickListener { handleLike(currentSong!!, songViewModel, playerViewModel) }
                setupLikeButton()

                // Handle pause button
                binding.pausePlayContainer.setOnClickListener { playerViewModel.togglePause() }

                // Handle prev & next button
                binding.nextButton.setOnClickListener { switchSongs(Direction.NEXT) }
                binding.prevButton.setOnClickListener { switchSongs(Direction.PREVIOUS) }
            }
        }

        // SeekBar listener
        binding.songSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { if (fromUser) playerViewModel.seekTo(progress) }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { }
            override fun onStopTrackingTouch(seekBar: SeekBar?) { }
        })

        // Observe ViewModel state
        playerViewModel.duration.observe(viewLifecycleOwner) {
            binding.songSeekBar.max = it
            binding.songDurationTo.text = handleTime(it)
        }

        playerViewModel.currentTime.observe(viewLifecycleOwner) {
            binding.songSeekBar.progress = it
            binding.songDurationAt.text = handleTime(it)
        }

        playerViewModel.isPaused.observe(viewLifecycleOwner) { animatePauseIcon(it, binding.playIcon, binding.pauseIcon) }
    }

    // ---------------------------------------------------------------- Handle Song Playing ----------------------------------------------------------------------------------

    private fun playSong() {
        playerViewModel.setCurrentSong(currentSong!!)
        playerViewModel.playSong(currentSong!!.pathFile.toUri(), songViewModel)
    }

    private fun getNextSong(direction: Direction): Song? {
        val currentIndex = songList.indexOfFirst { it.id == currentSong?.id }

        return when (direction) {
            Direction.NEXT -> {
                if (currentIndex != -1 && currentIndex + 1 < songList.size) songList[currentIndex + 1] else null
            }

            Direction.PREVIOUS -> {
                if (currentIndex > 0) songList[currentIndex - 1] else null
            }
        }
    }

    private fun handleTime(ms: Int): String {
        val totalSec = ms / 1000

        val min = totalSec / 60
        val sec = totalSec % 60

        return String.format(Locale.US, "%d:%02d", min, sec)
    }

    private fun switchSongs(direction: Direction) {
        val nextSong = getNextSong(direction)
        if (nextSong != null) {
            currentSong = nextSong
            songId = nextSong.id

            // Refresh UI
            handleData()

            playerViewModel.setCurrentSong(nextSong)
            playerViewModel.playSong(nextSong.pathFile.toUri(), songViewModel)
        }
    }

    // ---------------------------------------------------------------- Handle Song Data  ----------------------------------------------------------------------------------
    private fun handleData() {
        binding.songTitle.text = currentSong!!.title
        binding.songArtist.text = currentSong!!.artist

        handleCover(requireContext(), currentSong!!.pathArtwork, binding.songCover, binding.root, "PlayerFragment")
    }

    // ---------------------------------------------------------------- Other  ----------------------------------------------------------------------------------

    private fun setupLikeButton() { binding.heartButton.background = if (currentSong!!.liked) ContextCompat.getDrawable(requireContext(), R.drawable.heart_filled) else null }
}