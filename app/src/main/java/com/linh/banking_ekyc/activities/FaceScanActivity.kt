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
import androidx.lifecycle.lifecycleScope
import com.google.firebase.messaging.FirebaseMessaging
import com.linh.banking_ekyc.ekyc.FaceScanScreen
import com.linh.banking_ekyc.network.RetrofitClient
import com.linh.banking_ekyc.network.SessionManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.coroutines.resume

class FaceScanActivity : ComponentActivity() {

    companion object {
        private const val TAG = "FaceScanActivity"
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
                    uploadPhotos(photos)
                }
            )
        }
    }

    private fun uploadPhotos(photos: List<ByteArray>) {
        val accessToken = SessionManager.getAccessToken(this)
        if (accessToken == null) {
            Toast.makeText(this, "Session expired. Please sign up again.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        lifecycleScope.launch {
            try {
                val fcmToken = getFcmToken()
                if (fcmToken == null) {
                    Toast.makeText(this@FaceScanActivity, "Failed to get FCM token", Toast.LENGTH_LONG).show()
                    finish()
                    return@launch
                }

                // Photos order: FACE_CENTER(0-2) → TURN_LEFT(3-5) → TURN_RIGHT(6-8)
                val frontFaces = photos.subList(0, 3)
                val leftFaces  = photos.subList(3, 6)
                val rightFaces = photos.subList(6, 9)

                fun List<ByteArray>.toParts(fieldName: String) = mapIndexed { i, bytes ->
                    MultipartBody.Part.createFormData(
                        fieldName, "$fieldName$i.jpg",
                        bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
                    )
                }

                val response = RetrofitClient.authApi.uploadPhotos(
                    authToken  = "Bearer $accessToken",
                    leftFaces  = leftFaces.toParts("left_faces"),
                    rightFaces = rightFaces.toParts("right_faces"),
                    frontFaces = frontFaces.toParts("front_faces"),
                    fcmToken   = fcmToken.toRequestBody("text/plain".toMediaTypeOrNull())
                )

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null && body.success && body.data != null) {
                        Log.d(TAG, "Upload successful! Session ID: ${body.data.sessionId}")
                        val intent = Intent(this@FaceScanActivity, EkycWaitingActivity::class.java)
                        intent.putExtra("session_id", body.data.sessionId)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(this@FaceScanActivity, body?.message ?: "Upload failed", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this@FaceScanActivity, "Upload failed: ${response.code()}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Upload error", e)
                Toast.makeText(this@FaceScanActivity, "Network error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun getFcmToken(): String? = suspendCancellableCoroutine { cont ->
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { Log.e(TAG, "FCM token failed", it); cont.resume(null) }
    }
}
