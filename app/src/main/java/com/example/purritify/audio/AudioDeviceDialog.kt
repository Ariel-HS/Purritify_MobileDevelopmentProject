package com.example.purritify.audio

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.purritify.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class AudioDeviceDialog(
    private val audioDeviceManager: AudioDeviceManager,
    private val onDeviceSelected: (AudioDevice) -> Unit
) : DialogFragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var emptyText: TextView
    private lateinit var adapter: AudioDeviceAdapter

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = layoutInflater
            .inflate(R.layout.dialog_audio_devices, null)

        recyclerView = view.findViewById(R.id.devicesList)
        loadingIndicator = view.findViewById(R.id.loadingIndicator)
        emptyText = view.findViewById(R.id.emptyDevicesText)

        adapter = AudioDeviceAdapter { device ->
            audioDeviceManager.selectDevice(device)
            onDeviceSelected(device)
            dismiss()
        }

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        // Observe the device list
        audioDeviceManager.audioDevices.observe(this) { devices ->
            adapter.submitList(devices)

            if (devices.isEmpty()) {
                emptyText.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            } else {
                emptyText.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
            }

            loadingIndicator.visibility = View.GONE
        }

        // Refresh devices when the dialog is shown
        loadingIndicator.visibility = View.VISIBLE
        audioDeviceManager.refreshAudioDevices()

        return MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .setTitle(R.string.select_audio_device)
            .setNegativeButton(android.R.string.cancel) { _, _ -> dismiss() }
            .create()
    }
}
