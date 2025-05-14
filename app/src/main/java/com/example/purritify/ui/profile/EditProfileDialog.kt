package com.example.purritify.ui.profile

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.DialogFragment
import com.example.purritify.databinding.DialogEditProfileBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.InputStream
import java.util.Locale


class EditProfileDialog(
    private val onSubmit: (location: String?, profilePhoto: MultipartBody.Part?) -> Unit
): DialogFragment() {
    private lateinit var binding: DialogEditProfileBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var selectedImageUri: Uri? = null

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedImageUri = uri
        binding.uploadPhotoButton.setImageURI(uri)
    }

    private var selectedLocation: String? = null

    private var currentLocation: String? = null

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            getCurrentLocation()
        } else {
            Toast.makeText(requireContext(), "Izin lokasi ditolak", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("MemberExtensionConflict")
    private val locationPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val locationUri = result.data?.data
            locationUri?.let {
                selectedLocation = it.toString()
                binding.titleInput.setText(selectedLocation)
            }
        }
    }

    @SuppressLint("MemberExtensionConflict")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreateDialog(savedInstanceState)

        // Location
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        checkLocationPermission()

        // Initialize dialog
        binding = DialogEditProfileBinding.inflate(layoutInflater)

        val dialog = AlertDialog.Builder(requireContext()).setView(binding.root).create()

        // Profile Photo
        binding.uploadPhotoButton.setOnClickListener { imagePickerLauncher.launch("image/*") }

        // Location Button
        binding.locationButton.setOnClickListener {
            openGoogleMaps()
        }

        binding.btnSave.setOnClickListener {
            val newLocation: String? = binding.titleInput.text?.toString()?.uppercase().takeUnless { it.isNullOrBlank() }

            val newProfilePhoto: MultipartBody.Part? = selectedImageUri?.let {
                createImagePartFromUri(requireContext(), it)
            }

            onSubmit(newLocation, newProfilePhoto)
            dialog.dismiss()
        }

        binding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        return dialog
    }

    private fun createImagePartFromUri(context: Context, uri: Uri): MultipartBody.Part? {
        val contentResolver = context.contentResolver
        val inputStream: InputStream = contentResolver.openInputStream(uri) ?: return null
        val byteArray = inputStream.readBytes()
        val requestBody = byteArray.toRequestBody("image/*".toMediaTypeOrNull())
        val fileName = "profile_${System.currentTimeMillis()}.jpg"
        return MultipartBody.Part.createFormData("profilePhoto", fileName, requestBody)
    }

    // ---- LOCATION ----
    private fun getCountryCodeFromLocation(location: Location): String? {
        val geocoder = Geocoder(requireContext(), Locale.getDefault())
        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)

        return addresses?.firstOrNull()?.countryCode
    }

    // Get location automatically
    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            getCurrentLocation()
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
    private fun getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        currentLocation = getCountryCodeFromLocation(location)

                        binding.titleInput.setText(currentLocation)
                    } else {
                        Log.d("Location", "Location is null")
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("Location", "Failed to get location", e)
                }
        } else {
            Log.w("Location", "Location permission not granted when trying to get location.")
        }
    }

    // Select location manually
    private fun openGoogleMaps() {
        val uri = "geo:0,0?q=locate+me".toUri()
        val intent = Intent(Intent.ACTION_VIEW, uri)

        intent.setPackage("com.google.android.apps.maps")
        locationPickerLauncher.launch(intent)
    }
}