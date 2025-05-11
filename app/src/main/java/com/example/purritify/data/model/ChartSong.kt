package com.example.purritify.data.model

data class ChartSong(
    val id: Int,
    val title: String,
    val artist: String,
    val artwork: String,
    val url: String,
    val duration: String,
    val country: String,
    val rank: Int,
    val createdAt: String,
    val updatedAt: String
)
