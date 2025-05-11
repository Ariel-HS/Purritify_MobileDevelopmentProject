package com.example.purritify.ui.chart

import androidx.recyclerview.widget.DiffUtil
import com.example.purritify.data.model.ChartSong
import com.example.purritify.data.room.Song

class ChartDiffCallback(
    private val oldSongs: List<ChartSong>,
    private val newSongs: List<ChartSong>
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