package com.example.purritify.ui.library

import android.content.ClipData.Item
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.purritify.R
import com.example.purritify.data.room.Song
import com.example.purritify.data.room.SongDatabase
import com.example.purritify.databinding.SongItemBinding

class SongAdapter(
    private val listener: ItemClickListener
) :
    RecyclerView.Adapter<SongAdapter.ViewHolder>() {

    private var songs = emptyList<Song>()

    fun setSongs(songs: List<Song>) {
        val songDiffCallback = SongDiffCallback(this.songs, songs)
        val diffResult = DiffUtil.calculateDiff(songDiffCallback)
        this.songs = songs
        diffResult.dispatchUpdatesTo(this)
    }

    class ViewHolder(
        private val view: SongItemBinding,
        private val listener: ItemClickListener
    ) : RecyclerView.ViewHolder(view.root) {
        val title = view.songTitle
        val artist = view.songArtist
        val artwork = view.songArtwork

        fun bind(song: Song) {
            view.apply {
                title.text = song.title
                artist.text = song.artist

                // TODO("SETUP ARTWORK IMAGE")
            }
        }
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): SongAdapter.ViewHolder {
        // Create a new view, which defines the UI of the list item
        val view = SongItemBinding.inflate(LayoutInflater.from(viewGroup.context), viewGroup, false)

        return ViewHolder(view, listener)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        viewHolder.itemView.setOnClickListener {
            listener.onItemClickListener(songs[position])
        }
        viewHolder.bind(songs[position])
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = songs.size

    interface ItemClickListener {
        fun onItemClickListener(song: Song)
    }
}