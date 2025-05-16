package com.example.purritify.ui.player

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.SeekBar
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import com.example.purritify.R
import com.example.purritify.data.model.PlayableSong
import com.example.purritify.data.room.Song
import com.example.purritify.databinding.MiniplayerBinding
import com.example.purritify.ui.library.SongViewModel

class MiniplayerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle) {
    private val binding: MiniplayerBinding
    private var songViewModelCache: SongViewModel? = null

    init {
        val inflater = LayoutInflater.from(context)
        binding = MiniplayerBinding.inflate(inflater, this, true)
    }

    private var fragmentManager: FragmentManager? = null

    private var currentSong: PlayableSong? = null

    private fun bindSong(song: PlayableSong?)  {
        if (song != null) {
            currentSong = song
            binding.songTitle.text = song.title
            binding.songArtist.text = song.artist

            // Load cover
            handleCover(context, song.pathArtwork, binding.songCover, binding.cardView, "Miniplayer")

            // remove like button if it's an online song
            if (song is Song) {
                binding.addButton.visibility = GONE
            } else {
                val shareButton = binding.shareButton

                shareButton.visibility = VISIBLE
                shareButton.setOnClickListener { currentSong?.let { ShareDialog(it.id).show(fragmentManager!!, "ShareDialog") } }
            }

            visibility = VISIBLE
        } else visibility = GONE
    }

    private fun updatePauseIcon(isPaused: Boolean) { animatePauseIcon(isPaused, binding.playIcon, binding.pauseIcon) }

    private fun setOnPause(action: () -> Unit) { binding.pausePlayContainer.setOnClickListener { action() } }

    private fun setOnLike(songViewModel: SongViewModel, song: Song, playerViewModel: PlayerViewModel) {
        binding.addButton.setOnClickListener {
            handleLike(song, songViewModel, playerViewModel)
        }
    }

    private fun updateDuration(duration: Int) {
        binding.songSeekBar.max = duration
    }

    private fun updateCurrentTime(currentTime: Int) {
        binding.songSeekBar.progress = currentTime
    }

    private fun setOnSeek(action: (Int) -> Unit) {
        binding.songSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) action(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setOnMiniplayerClick(action: (PlayableSong) -> Unit) {
        binding.cardView.setOnClickListener {
            currentSong?.let { action(it) }
        }
    }

    fun activateMiniplayer(lifecycleOwner: LifecycleOwner, playerViewModel: PlayerViewModel, songViewModel: SongViewModel?, fragmentManager: FragmentManager, onClick: (PlayableSong) -> Unit) {
        // Cache the ViewModel if it's not null
        if (songViewModel != null) {
            songViewModelCache = songViewModel
        }

        this.fragmentManager = fragmentManager

        playerViewModel.currentSong.observe(lifecycleOwner) {
            bindSong(it)

            if (it is Song) {
                val viewModelToUse = songViewModelCache
                if (viewModelToUse != null) {
                    setOnLike(viewModelToUse, it, playerViewModel)
                    binding.addButton.setImageResource(if (it.liked) R.drawable.like else R.drawable.unlike)
                    binding.addButton.visibility = VISIBLE
                } else {
                    // Should never be called, but ok
                    binding.addButton.visibility = GONE
                }
            } else {
                binding.addButton.visibility = GONE
            }

            // TODO check if have to nullify cache when playing online song

            setOnMiniplayerClick(onClick)
        }

        playerViewModel.isPaused.observe(lifecycleOwner) { updatePauseIcon(it) }
        playerViewModel.duration.observe(lifecycleOwner) { updateDuration(it) }
        playerViewModel.currentTime.observe(lifecycleOwner) { updateCurrentTime(it) }
        setOnPause { playerViewModel.togglePause() }
        setOnSeek { playerViewModel.seekTo(it) }
    }
}