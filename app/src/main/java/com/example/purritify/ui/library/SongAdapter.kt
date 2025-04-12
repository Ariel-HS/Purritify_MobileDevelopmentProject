package com.example.purritify.ui.library

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.purritify.R
import com.example.purritify.data.room.Song
import androidx.core.net.toUri
import com.example.purritify.databinding.LibrarySongCardBinding

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
        private val view: LibrarySongCardBinding,
        private val listener: ItemClickListener
    ) : RecyclerView.ViewHolder(view.root) {
        val title = view.songTitle
        val artist = view.songArtist
        private val artwork = view.songArtwork

        fun bind(song: Song) {
            view.apply {
                title.text = song.title
                artist.text = song.artist

                try {
                    artwork.setImageURI(song.pathArtwork.toUri())
                } catch (e: Exception) {
                    Toast.makeText(itemView.context, "Failed to load image: ${e.message}", Toast.LENGTH_SHORT).show()
                    e.printStackTrace()
                }

                if (song.liked) {
                    likeButton.setImageResource(R.drawable.liked_24)
                } else {
                    likeButton.setImageResource(R.drawable.like_plus_circle_24)
                }

                likeButton.setOnClickListener {
                    listener.onLikeButtonClickListener(song)
                }

                deleteButton.setOnClickListener {
                    listener.onDeleteButtonClickListener(song)
                }

                editButton.setOnClickListener {
                    listener.onEditButtonClickListener(song)
                }
            }
        }
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item
        val view = LibrarySongCardBinding.inflate(LayoutInflater.from(viewGroup.context), viewGroup, false)

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
        fun onLikeButtonClickListener(song: Song)
        fun onDeleteButtonClickListener(song: Song)
        fun onEditButtonClickListener(song: Song)
    }
}