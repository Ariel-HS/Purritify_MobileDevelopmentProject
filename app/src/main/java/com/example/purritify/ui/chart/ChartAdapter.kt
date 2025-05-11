package com.example.purritify.ui.chart

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.purritify.data.model.ChartSong
import com.example.purritify.databinding.ChartSongCardBinding

class ChartAdapter(
    private val listener: ItemClickListener
) :
    RecyclerView.Adapter<ChartAdapter.ViewHolder>() {

    private var songs = emptyList<ChartSong>()

    fun setSongs(songs: List<ChartSong>) {
        val songDiffCallback = ChartDiffCallback(this.songs, songs)
        val diffResult = DiffUtil.calculateDiff(songDiffCallback)
        this.songs = songs
        diffResult.dispatchUpdatesTo(this)
    }

    class ViewHolder(
        private val view: ChartSongCardBinding,
    ) : RecyclerView.ViewHolder(view.root) {
        val title = view.songTitle
        val artist = view.songArtist
        private val artwork = view.songArtwork

        fun bind(song: ChartSong) {
            view.apply {
                title.text = song.title
                artist.text = song.artist

                Glide.with(view.root.context).
                load(song.artwork)
                    .into(artwork)
            }
        }
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item
        val view = ChartSongCardBinding.inflate(LayoutInflater.from(viewGroup.context), viewGroup, false)

        return ViewHolder(view)
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
        fun onItemClickListener(song: ChartSong)
    }
}