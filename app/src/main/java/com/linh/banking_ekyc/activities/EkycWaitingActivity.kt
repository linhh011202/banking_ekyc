package com.linh.banking_ekyc.activities

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.firebase.messaging.FirebaseMessaging
import com.linh.banking_ekyc.databinding.ActivityEkycWaitingBinding
import com.linh.banking_ekyc.network.RetrofitClient
import com.linh.banking_ekyc.network.SessionManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.coroutines.resume

class EkycWaitingActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "EkycWaiting"
        const val ACTION_EKYC_RESULT = "com.linh.banking_ekyc.EKYC_RESULT"
        const val EXTRA_EVENT = "event"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_SUCCESS = "success"
    }

    private lateinit var binding: ActivityEkycWaitingBinding
    private var sessionId: String? = null
    private var flow: String? = null
    private var email: String? = null

    private val ekycResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val event = intent?.getStringExtra(EXTRA_EVENT) ?: return
            val resultSessionId = intent.getStringExtra(EXTRA_SESSION_ID)
            val success = intent.getBooleanExtra(EXTRA_SUCCESS, false)

            // Only handle if it matches our session
            if (sessionId != null && resultSessionId != sessionId) return

            showResult(event, success)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEkycWaitingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionId = intent.getStringExtra("session_id")
        flow = intent.getStringExtra("flow")
        email = intent.getStringExtra("email")
        binding.sessionTxt.text = "Session: ${sessionId ?: "—"}"

        // Register for eKYC result broadcasts
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(ekycResultReceiver, IntentFilter(ACTION_EKYC_RESULT))

        // If photos are pending, upload them now
        val signUpPhotos = FaceScanActivity.pendingPhotos
        val loginPhotos = EkycLoginFaceScanActivity.pendingPhotos

        if (signUpPhotos != null) {
            FaceScanActivity.pendingPhotos = null
            binding.subtitleTxt.text = "Uploading photos..."
            uploadSignUpPhotos(signUpPhotos)
        } else if (loginPhotos != null) {
            EkycLoginFaceScanActivity.pendingPhotos = null
            binding.subtitleTxt.text = "Uploading photos..."
            uploadLoginPhotos(loginPhotos)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this)
            .unregisterReceiver(ekycResultReceiver)
    }

    // ── Sign-up photo upload ─────────────────────────────────────────────

    private fun uploadSignUpPhotos(photos: List<ByteArray>) {
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
                    showUploadError("Failed to get FCM token")
                    return@launch
                }

                binding.subtitleTxt.text = "Uploading ${photos.size} photos..."

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
                        sessionId = body.data.sessionId
                        Log.d(TAG, "Upload successful! Session ID: $sessionId")
                        binding.sessionTxt.text = "Session: $sessionId"
                        binding.subtitleTxt.text = "Your photos are being processed.\nThis may take a moment."
                    } else {
                        showUploadError(body?.message ?: "Upload failed")
                    }
                } else {
                    showUploadError("Upload failed: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Upload error", e)
                showUploadError("Network error: ${e.localizedMessage}")
            }
        }
    }

    // ── Login photo upload ───────────────────────────────────────────────

    private fun uploadLoginPhotos(photos: List<ByteArray>) {
        lifecycleScope.launch {
            try {
                val fcmToken = getFcmToken()
                if (fcmToken == null) {
                    showUploadError("Failed to get FCM token")
                    return@launch
                }

                binding.subtitleTxt.text = "Uploading ${photos.size} photos..."

                val faceParts = photos.mapIndexed { i, bytes ->
                    MultipartBody.Part.createFormData(
                        "faces", "face$i.jpg",
                        bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
                    )
                }

                val response = RetrofitClient.authApi.ekycLogin(
                    email    = (email ?: "").toRequestBody("text/plain".toMediaTypeOrNull()),
                    fcmToken = fcmToken.toRequestBody("text/plain".toMediaTypeOrNull()),
                    faces    = faceParts
                )

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null && body.success && body.data != null) {
                        sessionId = body.data.sessionId
                        Log.d(TAG, "eKYC login upload successful! Session ID: $sessionId")
                        binding.sessionTxt.text = "Session: $sessionId"
                        binding.subtitleTxt.text = "Your photos are being processed.\nThis may take a moment."
                    } else {
                        showUploadError(body?.message ?: "eKYC login failed")
                    }
                } else {
                    showUploadError("eKYC login failed: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "eKYC login upload error", e)
                showUploadError("Network error: ${e.localizedMessage}")
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun showUploadError(message: String) {
        binding.subtitleTxt.text = message
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()

        // Show retry button
        binding.waitingLayout.visibility = View.GONE
        binding.resultLayout.visibility = View.VISIBLE
        binding.resultIconTxt.text = "✗"
        binding.resultTitleTxt.text = "Upload Failed"
        binding.resultMessageTxt.text = "$message\nPlease try again."
        binding.actionBtn.text = "Try Again"
        binding.actionBtn.setOnClickListener {
            navigateBack()
        }
    }

    private fun navigateBack() {
        if (flow == "ekyc_login") {
            val intent = Intent(this, EkycLoginEmailActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        } else {
            val intent = Intent(this, SignUpActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
        finish()
    }

    private suspend fun getFcmToken(): String? = suspendCancellableCoroutine { cont ->
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { Log.e(TAG, "FCM token failed", it); cont.resume(null) }
    }

    private fun showResult(event: String, success: Boolean) {
        binding.waitingLayout.visibility = View.GONE
        binding.resultLayout.visibility = View.VISIBLE

        if (success) {
            binding.resultIconTxt.text = "✓"
            binding.resultTitleTxt.text = "Verification Successful!"
            binding.resultMessageTxt.text = if (event == "sign_up") {
                "Your face has been registered successfully.\nWelcome aboard!"
            } else if (event == "sign_in") {
                "Face login successful.\nWelcome back!"
            } else {
                "Face verification successful."
            }
            binding.actionBtn.text = "Continue"
            binding.actionBtn.setOnClickListener {
                if (flow == "ekyc_login" && email != null) {
                    SessionManager.saveSession(this, email!!, "ekyc_login_session")
                }
                val intent = Intent(this, BankingMainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
        } else {
            binding.resultIconTxt.text = "✗"
            binding.resultTitleTxt.text = "Verification Failed"
            binding.resultMessageTxt.text = if (event == "sign_up") {
                "Face registration failed.\nPlease try again."
            } else if (event == "sign_in") {
                "Face login failed.\nPlease try again."
            } else {
                "Face verification failed.\nPlease try again."
            }
            binding.actionBtn.text = "Try Again"
            binding.actionBtn.setOnClickListener {
                navigateBack()
            }
        }
    }
}
