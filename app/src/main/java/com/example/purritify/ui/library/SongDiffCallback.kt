package com.example.purritify.ui.library

import androidx.recyclerview.widget.DiffUtil
import com.example.purritify.data.room.Song

class SongDiffCallback(
    private val oldSongs: List<Song>,
    private val newSongs: List<Song>
) : DiffUtil.Callback() {
    override fun getOldListSize(): Int {
        return oldSongs.size
    }

    override fun getNewListSize(): Int {
        return newSongs.size
    }

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldSongs[oldItemPosition].id == newSongs[newItemPosition].id
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldSongs[oldItemPosition] == newSongs[newItemPosition]
    }

}