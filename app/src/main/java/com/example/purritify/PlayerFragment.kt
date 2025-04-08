package com.example.purritify

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
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
import com.example.purritify.data.room.SongEvent
import com.example.purritify.ui.player.animatePauseIcon
import com.example.purritify.ui.player.handleCover

enum class Direction {
    NEXT, PREVIOUS
}

class PlayerFragment : Fragment() {
    private var songId: Int = -1
    private var currentSong: Song? = null

    private lateinit var binding: FragmentPlayerBinding
    private lateinit var playerViewModel: PlayerViewModel

    private lateinit var songViewModel: SongViewModel

    private lateinit var songDatabase: SongDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            songId = it.getInt("arg_song_id", -1)
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
            SongViewModelFactory(songDatabase.songDao)
        )[SongViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentPlayerBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Handle close button
        binding.closeButton.setOnClickListener { findNavController().popBackStack() }

        // For testing
        songId = 1

        // Get song
        viewLifecycleOwner.lifecycleScope.launch {
            songViewModel.getSongById(songId).collect { song ->
                if (song == null) {
                    Log.d("PlayerFragment", "No song found with id $songId")

                    // Send back to home
                    findNavController().popBackStack()
                } else {
                    currentSong = song
                    Log.d("PlayerFragment", "Song loaded: $song")

                    handleData()

                    // Start player if not currently playing & not currently paused
                    if (!playerViewModel.isPlaying() && playerViewModel.isPaused.value != true) {
                        playerViewModel.setCurrentSong(currentSong!!)
                        playerViewModel.playSong(currentSong!!.pathFile.toUri())
                    }

                    // Handle pause button
                    binding.pausePlayContainer.setOnClickListener { playerViewModel.togglePause() }

                    // Handle like button
                    setupLikeButton()
                    binding.heartButton.setOnClickListener { handleLike() }

                    // Handle prev & next button
                    binding.nextButton.setOnClickListener { switchSongs(Direction.NEXT) }
                    binding.prevButton.setOnClickListener { switchSongs(Direction.PREVIOUS) }
                }
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

        playerViewModel.isPaused.observe(viewLifecycleOwner) { paused ->
            animatePauseIcon(paused, binding.playIcon, binding.pauseIcon)
        }
    }

    // ---------------------------------------------------------------- Handle Song Playing ----------------------------------------------------------------------------------

    private fun handleTime(ms: Int): String {
        val totalSec = ms / 1000

        val min = totalSec / 60
        val sec = totalSec % 60

        return String.format(Locale.US, "%d:%02d", min, sec)
    }

    private fun switchSongs(direction: Direction) {
        viewLifecycleOwner.lifecycleScope.launch {
            val id = currentSong!!.id

            val nextSong = when (direction) {
                Direction.NEXT -> songViewModel.getNextSong(id)
                Direction.PREVIOUS -> songViewModel.getPreviousSong(id)
            }

            if (nextSong != null) {
                currentSong = nextSong

                // Refresh UI
                handleData()

                playerViewModel.setCurrentSong(nextSong)
                playerViewModel.playSong(nextSong.pathFile.toUri())
            }
        }
    }

    // ---------------------------------------------------------------- Handle Song Data  ----------------------------------------------------------------------------------
    private fun handleData() {
        binding.songTitle.text = currentSong!!.title
        binding.songArtist.text = currentSong!!.artist

        handleCover(requireContext(), currentSong!!.pathArtwork, binding.songCover, binding.root, "PlayerFragment")
    }

    // ---------------------------------------------------------------- Other  ----------------------------------------------------------------------------------

    private fun setupLikeButton() {
        val heart = binding.heartButton
        val isLiked = currentSong!!.liked

        if (isLiked) {
            heart.background = ContextCompat.getDrawable(requireContext(), R.drawable.heart_filled)
        } else {
            heart.background = null
        }
    }

    private fun handleLike() {
        val updatedSong = currentSong!!.copy(liked = !currentSong!!.liked)

        songViewModel.onEvent(SongEvent.UpsertSong(updatedSong))

        setupLikeButton()
    }

    companion object {
        private const val ARG_SONG_ID = "arg_song_id"

        fun newInstance(songId: Int): PlayerFragment {
            val fragment = PlayerFragment()
            val args = Bundle().apply {
                putInt(ARG_SONG_ID, songId)
            }

            fragment.arguments = args

            return fragment
        }
    }
}