package com.example.purritify

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import com.example.purritify.databinding.FragmentPlayerBinding
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.palette.graphics.Palette
import androidx.room.Room
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.example.purritify.data.room.Song
import com.example.purritify.data.room.SongDatabase
import com.example.purritify.ui.library.SongViewModel
import com.example.purritify.ui.library.SongViewModelFactory
import com.example.purritify.ui.player.PlayerViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import androidx.core.net.toUri

class PlayerFragment : Fragment() {
    private var songId: Int = -1
    private lateinit var binding: FragmentPlayerBinding

    private lateinit var playerViewModel: PlayerViewModel
    private lateinit var songViewModel: SongViewModel

    private lateinit var songDatabase: SongDatabase

    private var currentSong: Song? = null

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
                    if (!playerViewModel.isPlaying() && playerViewModel.isPaused.value != true)
                        playerViewModel.playSong(currentSong!!.pathFile.toUri())
                }
            }
        }


        // Handle pause button
        binding.pausePlayContainer.setOnClickListener { playerViewModel.togglePause() }

        // SeekBar listener
        binding.songSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { if (fromUser) playerViewModel.seekTo(progress) }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
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
            animatePauseIcon(paused)
        }
    }

    // ---------------------------------------------------------------- Handle Song Playing ----------------------------------------------------------------------------------
    private fun animatePauseIcon(isPaused: Boolean) {
        val fadeDuration = 80L

        val playIcon = binding.playIcon
        val pauseIcon = binding.pauseIcon

        if (isPaused) {
            pauseIcon.animate().alpha(0f).setDuration(fadeDuration).withEndAction {
                pauseIcon.visibility = View.GONE
                playIcon.visibility = View.VISIBLE
                playIcon.animate().alpha(1f).setDuration(fadeDuration).start()
            }.start()
        } else {
            playIcon.animate().alpha(0f).setDuration(fadeDuration).withEndAction {
                playIcon.visibility = View.GONE
                pauseIcon.visibility = View.VISIBLE
                pauseIcon.animate().alpha(1f).setDuration(fadeDuration).start()
            }.start()
        }
    }

    private fun handleTime(ms: Int): String {
        val totalSec = ms / 1000

        val min = totalSec / 60
        val sec = totalSec % 60

        return String.format(Locale.US, "%d:%02d", min, sec)
    }

    // ---------------------------------------------------------------- Handle Song Data  ----------------------------------------------------------------------------------
    private fun handleData() {
        binding.songTitle.text = currentSong!!.title
        binding.songArtist.text = currentSong!!.artist

        handleCover(currentSong!!.pathArtwork)
    }
    private fun handleCover(coverPath: String) {
        val file = File(coverPath)

        if (file.exists()) {
            val uri = Uri.fromFile(file)
            Glide.with(requireContext())
                .load(uri)
                .into(binding.songCover)

            adjustBGColor(uri)
        } else {
            Log.d("PlayerFragment", "Cover doesn't exist at $coverPath, defaulting to sample cover")

            binding.songCover.setImageResource(R.drawable.sample_cover)
            adjustBGColor(null)
        }
    }

    private fun adjustBGColor(coverUri: Uri?) {
        val cover = coverUri ?: R.drawable.sample_cover

        Glide.with(requireContext())
            .asBitmap()
            .load(cover)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(bitmap: Bitmap, transition: Transition<in Bitmap>?) {
                    Palette.from(bitmap).generate { palette ->
                        // Get dominant color
                        val dominantColor = palette?.getDominantColor(Color.BLACK) ?: Color.BLACK

                        // Adjust brightness
                        val bgColor1 = ColorUtils.blendARGB(dominantColor, Color.WHITE, 0.075f)
                        val bgColor2 = ColorUtils.blendARGB(dominantColor, Color.BLACK, 0.2f)

                        // Create gradient
                        val gradientDrawable = GradientDrawable(
                            GradientDrawable.Orientation.TOP_BOTTOM,
                            intArrayOf(bgColor1, bgColor2, Color.BLACK)
                        )

                        // Set as background color
                        binding.root.background = gradientDrawable
                    }
                }

                override fun onLoadCleared(placeholder: Drawable?) { }
            })
    }

    // ---------------------------------------------------------------- Other  ----------------------------------------------------------------------------------

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