package com.example.purritify.data.model

interface PlayableSong {
    val id: Int
    val title: String
    val artist: String
    val pathArtwork: String
    val pathFile: String
    val duration: String
//    val isOnline: Boolean
}