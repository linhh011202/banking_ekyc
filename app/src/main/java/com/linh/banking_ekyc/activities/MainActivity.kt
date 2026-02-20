package com.linh.banking_ekyc.activities

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.firebase.messaging.FirebaseMessaging
import com.linh.banking_ekyc.ekyc.FaceScanScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Thread {
            try {
                FirebaseMessaging.getInstance().deleteToken().addOnCompleteListener {
                    FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                        if (!task.isSuccessful) {
                            Log.w("FCM", "Fetching FCM registration token failed", task.exception)
                            return@addOnCompleteListener
                        }
                        val token = task.result
                        Log.d("FCM", "===== FCM Registration Token =====")
                        Log.d("FCM", "Token: $token")
                        Log.d("FCM", "==================================")
                    }
                }
            } catch (e: Exception) {
                Log.e("FCM", "Error deleting token", e)
            }
        }.start()

        setContent {
            EkycApp(onFinish = { finish() })
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun EkycApp(onFinish: () -> Unit = {}) {
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    if (cameraPermissionState.status.isGranted) {
        FaceScanScreen(
            onCancel = onFinish,
            onCompleted = {}
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Camera permission is required", color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                    Text("Grant camera permission")
                }
            }
        }
    }
}

