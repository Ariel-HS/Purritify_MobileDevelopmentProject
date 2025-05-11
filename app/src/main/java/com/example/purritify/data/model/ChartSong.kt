package com.example.purritify.data.model

import android.os.Parcelable
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
data class ChartSong(
    override val id: Int,
    override val title: String,
    override val artist: String,
    val artwork: String,
    val url: String,
    override val duration: String,
    val country: String,
    val rank: Int,
    val createdAt: String,
    val updatedAt: String
) : PlayableSong, Parcelable {
//    @IgnoredOnParcel
//    override val isOnline: Boolean = true

    override val pathArtwork: String get() = artwork
    override val pathFile: String get() = url
}
