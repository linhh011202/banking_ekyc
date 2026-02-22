package com.linh.banking_ekyc.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.linh.banking_ekyc.ekyc.FaceScanScreen
import com.linh.banking_ekyc.network.RetrofitClient

class FaceScanActivity : ComponentActivity() {

    companion object {
        private const val TAG = "FaceScanActivity"
        /** Temporary holder for captured photos to pass between activities without Parcel limit */
        @Volatile
        var pendingPhotos: List<ByteArray>? = null
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) startFaceScan()
        else {
            Toast.makeText(this, "Camera permission is required for eKYC", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Pre-warm server connection while user scans face
        RetrofitClient.prewarmConnection()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startFaceScan()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startFaceScan() {
        setContent {
            FaceScanScreen(
                onCancel = { finish() },
                onCompleted = { photos ->
                    Log.d(TAG, "Face scan completed with ${photos.size} photos")
                    // Navigate immediately — upload happens in EkycWaitingActivity
                    pendingPhotos = photos
                    val intent = Intent(this, EkycWaitingActivity::class.java)
                    intent.putExtra("flow", "sign_up")
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
            )
        }
    }
}
