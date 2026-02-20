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
import com.linh.banking_ekyc.ekyc.FaceLoginScanScreen
import com.linh.banking_ekyc.network.RetrofitClient
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.coroutines.resume

class EkycLoginFaceScanActivity : ComponentActivity() {

    companion object {
        private const val TAG = "EkycLoginFaceScan"
    }

    private var email: String = ""

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
        email = intent.getStringExtra("email") ?: ""
        if (email.isEmpty()) {
            Toast.makeText(this, "Email is required", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startFaceScan()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startFaceScan() {
        setContent {
            FaceLoginScanScreen(
                onCancel = { finish() },
                onCompleted = { photos ->
                    Log.d(TAG, "Face login scan completed with ${photos.size} photos")
                    uploadForEkycLogin(photos)
                }
            )
        }
    }

    private fun uploadForEkycLogin(photos: List<ByteArray>) {
        lifecycleScope.launch {
            try {
                val fcmToken = getFcmToken()
                if (fcmToken == null) {
                    Toast.makeText(this@EkycLoginFaceScanActivity, "Failed to get FCM token", Toast.LENGTH_LONG).show()
                    finish()
                    return@launch
                }

                Log.d(TAG, "Email: $email, Photos: ${photos.size}")

                val faceParts = photos.mapIndexed { i, bytes ->
                    MultipartBody.Part.createFormData(
                        "faces", "face$i.jpg",
                        bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
                    )
                }

                val response = RetrofitClient.authApi.ekycLogin(
                    email    = email.toRequestBody("text/plain".toMediaTypeOrNull()),
                    fcmToken = fcmToken.toRequestBody("text/plain".toMediaTypeOrNull()),
                    faces    = faceParts
                )

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null && body.success && body.data != null) {
                        Log.d(TAG, "eKYC login successful! Session ID: ${body.data.sessionId}")
                        val intent = Intent(this@EkycLoginFaceScanActivity, EkycWaitingActivity::class.java)
                        intent.putExtra("session_id", body.data.sessionId)
                        intent.putExtra("flow", "ekyc_login")
                        intent.putExtra("email", email)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(this@EkycLoginFaceScanActivity, body?.message ?: "eKYC login failed", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this@EkycLoginFaceScanActivity, "eKYC login failed: ${response.code()}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "eKYC login error", e)
                Toast.makeText(this@EkycLoginFaceScanActivity, "Network error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun getFcmToken(): String? = suspendCancellableCoroutine { cont ->
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { Log.e(TAG, "FCM token failed", it); cont.resume(null) }
    }
}
