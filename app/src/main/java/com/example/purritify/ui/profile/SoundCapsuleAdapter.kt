package com.example.purritify.ui.profile

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.net.toUri
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.purritify.R
import com.example.purritify.data.room.MonthlyStat
import com.example.purritify.data.room.StreakStat
import com.example.purritify.databinding.SoundCapsuleCardBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SoundCapsuleAdapter(
//    private val listener: ItemClickListener
) :
    RecyclerView.Adapter<SoundCapsuleAdapter.ViewHolder>() {

    private var monthlyStats: List<MonthlyStat> = emptyList()

    fun setStats(stats: List<MonthlyStat>) {
        val diffCallback = SoundCapsuleDiffCallback(this.monthlyStats, stats)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        this.monthlyStats = stats
        diffResult.dispatchUpdatesTo(this)
    }

    class ViewHolder(
        private val view: SoundCapsuleCardBinding,
//        private val listener: ItemClickListener
    ) : RecyclerView.ViewHolder(view.root) {

        fun bind(stats: MonthlyStat) {
            view.apply {
                month.text = stats.month
                totalTimeListened.text = root.context.getString(R.string.total_time_listened, (stats.timeListened.totalTime/60).toString())

                Log.d("CapsuleAdapter", stats.toString())
                artistName.text = stats.topArtist.artist

                songName.text = stats.topSong.title
                try {
                    songArtwork.setImageURI(stats.topSong.pathArtwork.toUri())
                } catch (e: Exception) {
                    Toast.makeText(itemView.context, "Failed to load image: ${e.message}", Toast.LENGTH_SHORT).show()
                    e.printStackTrace()
                }

                if (stats.streakSong == null) {
                    streakStats.visibility = View.GONE
                    return
                }

                val formatter = SimpleDateFormat("MM-yyyy", Locale.US)
                val now = formatter.format(Date())

                if (stats.month != now) {
                    return
                }

                val streakSong: StreakStat = stats.streakSong
                try {
                    streakArtwork.setImageURI(streakSong.pathArtwork.toUri())
                } catch (e: Exception) {
                    Toast.makeText(itemView.context, "Failed to load image: ${e.message}", Toast.LENGTH_SHORT).show()
                    e.printStackTrace()
                }
                streakAmount.text = root.context.getString(R.string.streak_amount, streakSong.streakLength)
                streakSubText.text = root.context.getString(R.string.streak_sub_text, streakSong.title, streakSong.artist)
                streakDate.text = root.context.getString(R.string.streak_date, streakSong.streakStart, streakSong.streakEnd)
            }
        }
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item
        val view = SoundCapsuleCardBinding.inflate(LayoutInflater.from(viewGroup.context), viewGroup, false)

//        return ViewHolder(view, listener)
        return ViewHolder(view)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        viewHolder.itemView.setOnClickListener {
//            listener.onItemClickListener(songs[position])
        }

        viewHolder.bind(monthlyStats[position])
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = monthlyStats.size

    interface ItemClickListener {
//        fun onItemClickListener(song: ChartSong)
//        fun onDownloadButtonClickListener(song: ChartSong)
    }
}