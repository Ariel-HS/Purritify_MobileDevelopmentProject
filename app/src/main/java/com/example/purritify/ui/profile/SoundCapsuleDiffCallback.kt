package com.example.purritify.ui.profile

import androidx.recyclerview.widget.DiffUtil
import com.example.purritify.data.model.ChartSong
import com.example.purritify.data.room.MonthlyStat
import com.example.purritify.data.room.Song

class SoundCapsuleDiffCallback(
    private val oldStats: List<MonthlyStat>,
    private val newStats: List<MonthlyStat>
) : DiffUtil.Callback() {
    override fun getOldListSize(): Int {
        return oldStats.size
    }

    override fun getNewListSize(): Int {
        return newStats.size
    }

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldStats[oldItemPosition].month == newStats[newItemPosition].month
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldStats[oldItemPosition] == newStats[newItemPosition]
    }

}