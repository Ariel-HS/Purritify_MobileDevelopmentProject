package com.example.purritify.ui.player

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import androidx.core.content.FileProvider
import androidx.core.net.toUri

import com.example.purritify.databinding.ShareDialogBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.journeyapps.barcodescanner.BarcodeEncoder
import java.io.File
import java.io.FileOutputStream

class ShareDialog (private var songId: Int): BottomSheetDialogFragment() {
    private lateinit var binding: ShareDialogBinding

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreateDialog(savedInstanceState)

        binding = ShareDialogBinding.inflate(layoutInflater)

        val dialog = AlertDialog.Builder(requireContext()).setView(binding.root).create()

        // Link
        binding.shareViaLink.setOnClickListener {
            handleSharingViaLink()

            dialog.dismiss()
        }

        // QR
        binding.shareViaQR.setOnClickListener {
            handleSharingViaQR()

            dialog.dismiss()
        }

        // Cancel
        binding.btnCancel.setOnClickListener { dialog.dismiss() }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setGravity(Gravity.BOTTOM)
        return dialog
    }

    private fun constructDeeplink(): Uri { return "purrytify://song/${songId}".toUri() }

    // Link
    private fun handleSharingViaLink() {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "${constructDeeplink()}")
        }

        startActivity(Intent.createChooser(shareIntent, "Share link"))
    }

    // QR
    @SuppressLint("MemberExtensionConflict")
    private fun generateQRCode(size: Int = 512): Bitmap? {
        return try {
            val bitMatrix: BitMatrix = MultiFormatWriter().encode(
                constructDeeplink().toString(),
                BarcodeFormat.QR_CODE,
                size,
                size
            )

            val barcodeEncoder = BarcodeEncoder()
            val bitmap = barcodeEncoder.createBitmap(bitMatrix)
            Log.d("ShareDialog", "QR Code Created: ${bitmap}")

            bitmap
        } catch (e: Exception) {
            Log.d("ShareDialog", "Failed to create QR code: ${e.message}")
            null
        }
    }

    private fun handleSharingViaQR() {
        val context = requireContext()
        val bitmap = generateQRCode()

        bitmap?.let {
            try {
                val file = File(context.cacheDir, "shared_qr.png")
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    out.flush()
                }

                val uri: Uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )

                // Create share intent
                val shareIntent: Intent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_STREAM, uri)
                    type = "image/png"
                    clipData = ClipData.newRawUri(null, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                // Start Android Share sheet
                startActivity(Intent.createChooser(shareIntent, "Share QR Code"))
            } catch (e: Exception) {
                Log.e("ShareDialog", "Error while preparing QR share: ${e.message}", e)
            }
        } ?: Log.e("ShareDialog", "QR bitmap was null, aborting share")
    }
}