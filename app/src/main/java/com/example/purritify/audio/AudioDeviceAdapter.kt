package com.example.purritify.audio

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.purritify.R

class AudioDeviceAdapter(
    private val onDeviceSelected: (AudioDevice) -> Unit
) : ListAdapter<AudioDevice, AudioDeviceAdapter.AudioDeviceViewHolder>(AudioDeviceDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AudioDeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_audio_device, parent, false)
        return AudioDeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: AudioDeviceViewHolder, position: Int) {
        val device = getItem(position)
        holder.bind(device)
        holder.itemView.setOnClickListener {
            onDeviceSelected(device)
        }
    }

    class AudioDeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val deviceName: TextView = itemView.findViewById(R.id.deviceName)
        private val deviceStatus: TextView = itemView.findViewById(R.id.deviceStatus)
        private val deviceIcon: ImageView = itemView.findViewById(R.id.deviceIcon)
        private val deviceSelected: RadioButton = itemView.findViewById(R.id.deviceSelected)

        fun bind(device: AudioDevice) {
            deviceName.text = device.name
            deviceStatus.text = if (device.isConnected) {
                itemView.context.getString(R.string.audio_device_status_connected)
            } else {
                itemView.context.getString(R.string.audio_device_status_disconnected)
            }
            deviceSelected.isChecked = device.isActive

            val iconResource = when (device.type) {
                AudioDeviceType.BLUETOOTH_A2DP,
                AudioDeviceType.BLUETOOTH_HEADSET -> R.drawable.audio_output // Use the same icon for now
                AudioDeviceType.WIRED_HEADSET -> R.drawable.audio_output
                AudioDeviceType.INTERNAL_SPEAKER -> R.drawable.audio_output
                else -> R.drawable.audio_output
            }
            deviceIcon.setImageResource(iconResource)
        }
    }

    private class AudioDeviceDiffCallback : DiffUtil.ItemCallback<AudioDevice>() {
        override fun areItemsTheSame(oldItem: AudioDevice, newItem: AudioDevice): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: AudioDevice, newItem: AudioDevice): Boolean {
            return oldItem == newItem
        }
    }
}
