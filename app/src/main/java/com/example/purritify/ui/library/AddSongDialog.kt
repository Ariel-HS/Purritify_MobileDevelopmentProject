package com.example.purritify.ui.library

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.example.purritify.data.room.Song
import com.example.purritify.data.room.SongEvent
import com.example.purritify.databinding.DialogAddSongBinding
import com.google.android.material.internal.TextWatcherAdapter
import okhttp3.internal.idn.binarySearch
import androidx.core.graphics.drawable.toDrawable
import com.example.purritify.R

class AddSongDialogFragment(
    private val onEvent: (SongEvent) -> Unit
): DialogFragment() {

    private lateinit var binding: DialogAddSongBinding
    private lateinit var imageResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var requestImagePermissionLauncher: ActivityResultLauncher<Array<String>>

    private lateinit var audioResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var requestAudioPermissionLauncher: ActivityResultLauncher<Array<String>>

    @SuppressLint("UseGetLayoutInflater")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreateDialog(savedInstanceState)

        binding = DialogAddSongBinding.inflate(LayoutInflater.from(context))

        setupListeners()

        registerImageResultLauncher()
        registerImageRequestPermissionLauncher()
        registerAudioResultLauncher()
        registerAudioRequestPermissionLauncher()

        val dialog = AlertDialog.Builder(requireActivity(), R.style.ProgressDialogTheme)
            .setView(binding.root)
            .create()

        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

        return dialog
    }

    override fun onCancel(dialog: DialogInterface) {
        onEvent(SongEvent.HideDialog)
        super.onCancel(dialog)
    }

    private fun setupListeners() {
        binding.titleInput.addTextChangedListener( object : TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

            override fun afterTextChanged(s: Editable) {
                onEvent(SongEvent.SetTitle(s.toString()))
            }
        })

        binding.artistInput.addTextChangedListener( object : TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

            override fun afterTextChanged(s: Editable) {
                onEvent(SongEvent.SetArtist(s.toString()))
            }
        })

        binding.btnSave.setOnClickListener {
            onEvent(SongEvent.SaveSong)
            onEvent(SongEvent.HideDialog)

            dismiss()
        }

        binding.btnCancel.setOnClickListener {
            onEvent(SongEvent.HideDialog)

            dismiss()
        }

        binding.uploadPhotoButton.setOnClickListener {
            Log.d("CHECK", "CLICK")
            checkStoragePermissionImage()
        }

        binding.uploadFileButton.setOnClickListener {
            Log.d("CHECK", "CLICK")
            checkStoragePermissionAudio()
        }
    }

    private fun registerImageResultLauncher() {
        imageResultLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                try {
                    val imageURI = result.data?.data
                    if (imageURI != null) {
                        if (!(this::binding.isInitialized && isAdded)) {
                            Log.d("CHECK", "binding fail")
                        }
                        binding.uploadPhotoButton.setImageURI(imageURI)
                        onEvent(SongEvent.SetPathArtwork(imageURI.toString()))

                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun registerImageRequestPermissionLauncher() {
        requestImagePermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions: Map<String, Boolean> ->
            if (!isAdded) {
                Log.d("CHECK", "not added fail")
            }

            val allGranted = permissions.all { it.value }

            if (allGranted) {
                pickImage()
            } else {
                Log.d("CHECK", "not all permissions granted: $permissions")
                // TODO Show Toast Message
            }
        }
    }

    private fun pickImage() {
        try {
            val galleryIntent = Intent(Intent.ACTION_PICK)
            galleryIntent.type = "image/*"

            imageResultLauncher.launch(galleryIntent)
        } catch (e: Exception) {
            // TODO MAKE TOAST
            e.printStackTrace()
        }
    }

    private fun checkStoragePermissionImage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Log.d("CHECK", "SDK 34")
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED) {
                Log.d("CHECK", "NOT GRANTED")
//                ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED), 1)
                requestImagePermissionLauncher.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED))

                return
            }

        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED) {
                Log.d("CHECK", "NOT GRANTED")
//                ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED), 1)
                requestImagePermissionLauncher.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES))

                return
            }
        } else {
            Log.d("CHECK", "SDK else")
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                Log.d("CHECK", "NOT GRANTED")
//                ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 1)
                requestImagePermissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))

                return
            }

        }

        pickImage()
    }

    private fun registerAudioRequestPermissionLauncher() {
        requestAudioPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions: Map<String, Boolean> ->
            if (!isAdded) {
                Log.d("CHECK", "not added fail")
            }

            val allGranted = permissions.all { it.value }

            if (allGranted) {
                pickAudio()
            } else {
                Log.d("CHECK", "not all permissions granted: $permissions")
                // TODO Show Toast Message
            }
        }
    }

    private fun registerAudioResultLauncher() {
        audioResultLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                try {
                    val audioURI: Uri? = result.data?.data
                    if (audioURI != null) {
                        if (!(this::binding.isInitialized && isAdded)) {
                            Log.d("CHECK", "binding fail")
                        }

                        val metadataRetriever = MediaMetadataRetriever()
                        metadataRetriever.setDataSource(requireContext(), audioURI)

                        val title = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                        val artist = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                        val duration = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)

                        Log.d("CHECK TITLE", title.toString())
                        Log.d("CHECK ARTIST", artist.toString())
                        Log.d("CHECK DURATION", duration.toString())

                        Log.d("TEST", metadataRetriever.toString())

                        binding.titleInput.setText(title)
                        binding.artistInput.setText(artist)
                        binding.durationTextView.text = getString(R.string.add_song_dialog_duration_text, duration)
                        binding.durationTextView.visibility = View.VISIBLE

                        onEvent(SongEvent.SetPathFile(audioURI.toString()))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun pickAudio() {
        try {
            val audioIntent = Intent(Intent.ACTION_GET_CONTENT)
            audioIntent.type = "audio/*"

            audioResultLauncher.launch(audioIntent)
        } catch (e: Exception) {
            // TODO MAKE TOAST
            e.printStackTrace()
        }
    }

    private fun checkStoragePermissionAudio() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
                Log.d("CHECK", "NOT GRANTED")
//                ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED), 1)
                requestAudioPermissionLauncher.launch(arrayOf(Manifest.permission.READ_MEDIA_AUDIO))

                return
            }
        } else {
            Log.d("CHECK", "SDK else")
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                Log.d("CHECK", "NOT GRANTED")
//                ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 1)
                requestAudioPermissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))

                return
            }

        }

        pickAudio()
    }
}