package com.example.purritify.ui.library.dialog

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.DialogFragment
import com.example.purritify.R
import com.example.purritify.data.room.Song
import com.example.purritify.data.room.SongEvent
import com.example.purritify.databinding.DialogAddSongBinding
import com.example.purritify.databinding.DialogDeleteSongBinding

class DeleteSongDialogFragment(
    private val onEvent: (SongEvent) -> Unit,
    private val song: Song
): DialogFragment() {

    private lateinit var binding: DialogDeleteSongBinding

    @SuppressLint("UseGetLayoutInflater")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreateDialog(savedInstanceState)

        binding = DialogDeleteSongBinding.inflate(LayoutInflater.from(context))

        setupListeners()

        val dialog = AlertDialog.Builder(requireActivity(), R.style.ProgressDialogTheme)
            .setView(binding.root)
            .create()

        val window = dialog.window
//        window?.setGravity(Gravity.BOTTOM)
        window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

        return dialog
    }

    private fun setupListeners() {

        binding.btnDelete.setOnClickListener {

            onEvent(SongEvent.DeleteSong(song))
            onEvent(SongEvent.HideDialog)

            dismiss()
        }

        binding.btnCancel.setOnClickListener {
            onEvent(SongEvent.HideDialog)

            dismiss()
        }

    }
}