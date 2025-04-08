package com.example.purritify.ui.player

import android.content.Context
import android.graphics.drawable.LayerDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.SeekBar
import androidx.lifecycle.LifecycleOwner
import com.example.purritify.data.room.Song
import com.example.purritify.databinding.MiniplayerBinding

class MiniplayerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle) {

    private val binding: MiniplayerBinding

    init {
        val inflater = LayoutInflater.from(context)
        binding = MiniplayerBinding.inflate(inflater, this, true)
    }

    private fun bindSong(song: Song?)  {
        if (song != null) {
            binding.songTitle.text = song.title
            binding.songArtist.text = song.artist

            // Load cover
            val seekbarDrawable = binding.songSeekBar.progressDrawable as LayerDrawable

            handleCover(context, song.pathArtwork, binding.songCover, binding.cardView, "Miniplayer", seekbarDrawable)

            visibility = VISIBLE
        } else visibility = GONE
    }

    private fun updatePauseIcon(isPaused: Boolean) { animatePauseIcon(isPaused, binding.playIcon, binding.pauseIcon) }

    private fun setOnPause(action: () -> Unit) { binding.pausePlayContainer.setOnClickListener { action() } }

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

    fun activateMiniplayer(lifecycleOwner: LifecycleOwner, playerViewModel: PlayerViewModel) {
        playerViewModel.currentSong.observe(lifecycleOwner) { bindSong(it) }
        playerViewModel.isPaused.observe(lifecycleOwner) { updatePauseIcon(it) }
        playerViewModel.duration.observe(lifecycleOwner) { updateDuration(it) }
        playerViewModel.currentTime.observe(lifecycleOwner) { updateCurrentTime(it) }
        setOnPause { playerViewModel.togglePause() }
        setOnSeek { playerViewModel.seekTo(it) }
    }
}