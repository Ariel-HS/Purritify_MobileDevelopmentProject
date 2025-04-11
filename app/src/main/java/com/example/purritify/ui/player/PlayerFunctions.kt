package com.example.purritify.ui.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.ImageView
import androidx.core.graphics.ColorUtils
import androidx.core.net.toUri
import androidx.palette.graphics.Palette
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.example.purritify.R
import com.example.purritify.data.room.Song
import com.example.purritify.data.room.SongEvent
import com.example.purritify.ui.library.SongViewModel
import com.google.android.material.card.MaterialCardView
import java.io.File
import java.util.Date

fun handleCover(context: Context, coverPath: String, coverView: ImageView, backgroundView: View, tag: String) {
    // Load song cover then adjusts the background color to the dominant color of the cover

    try {
        Glide.with(context)
            .load(coverPath.toUri())
            .into(coverView)

        adjustBGColor(context, backgroundView, coverPath.toUri())
    } catch (e: Exception) {
        Log.d(tag, "Cover doesn't exist at $coverPath, defaulting to sample cover")

        coverView.setImageResource(R.drawable.sample_cover)
        adjustBGColor(context, backgroundView, null)
    }
}

fun adjustBGColor(context: Context, view: View, coverUri: Uri?) {
    // Adjust background color to the dominant color of the cover

    val cover = coverUri ?: R.drawable.sample_cover

    Glide.with(context)
        .asBitmap()
        .load(cover)
        .into(object : CustomTarget<Bitmap>() {
            override fun onResourceReady(bitmap: Bitmap, transition: Transition<in Bitmap>?) {
                Palette.from(bitmap).generate { palette ->
                    // Get dominant color
                    val dominantColor = palette?.getDominantColor(Color.BLACK) ?: Color.BLACK


                    if (view is MaterialCardView) { view.setCardBackgroundColor(dominantColor) }
                    else {
                        //  Adjust brightness
                        val bgColor1 = ColorUtils.blendARGB(dominantColor, Color.WHITE, 0.15f)
                        val bgColor2 = ColorUtils.blendARGB(dominantColor, Color.BLACK, 0.2f)

                        // Create gradient
                        val gradientDrawable = GradientDrawable(
                            GradientDrawable.Orientation.TOP_BOTTOM,
                            intArrayOf(bgColor1, bgColor2, Color.BLACK)
                        )

                        // Set as background color
                        view.background = gradientDrawable
                    }
                }
            }

            override fun onLoadCleared(placeholder: Drawable?) { }
        })
}

fun animatePauseIcon(isPaused: Boolean, playIconView: ImageView, pauseIconView: ImageView) {
    val fadeDuration = 80L

    if (isPaused) {
        pauseIconView.animate().alpha(0f).setDuration(fadeDuration).withEndAction {
            pauseIconView.visibility = View.GONE
            playIconView.visibility = View.VISIBLE
            playIconView.animate().alpha(1f).setDuration(fadeDuration).start()
        }.start()
    } else {
        playIconView.animate().alpha(0f).setDuration(fadeDuration).withEndAction {
            playIconView.visibility = View.GONE
            pauseIconView.visibility = View.VISIBLE
            pauseIconView.animate().alpha(1f).setDuration(fadeDuration).start()
        }.start()
    }
}

fun handleLike(song: Song, songViewModel: SongViewModel, playerViewModel: PlayerViewModel) {
    val updatedSong = song.copy(liked = !song.liked)

    songViewModel.onEvent(SongEvent.UpsertSong(updatedSong))

    playerViewModel.setCurrentSong(updatedSong)
}

fun handleLastPlayedUpdate(song: Song, songViewModel: SongViewModel, playerViewModel: PlayerViewModel) {
    val updatedSong = song.copy(lastPlayed = Date())

    songViewModel.onEvent(SongEvent.UpsertSong(updatedSong))

    playerViewModel.setCurrentSong(updatedSong)
}