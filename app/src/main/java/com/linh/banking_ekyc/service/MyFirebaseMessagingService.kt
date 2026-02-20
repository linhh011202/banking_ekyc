package com.linh.banking_ekyc.service

import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.linh.banking_ekyc.activities.EkycWaitingActivity

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCM"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "===== FCM Registration Token =====")
        Log.d(TAG, "Token: $token")
        Log.d(TAG, "==================================")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "Message received from: ${message.from}")

        message.notification?.let {
            Log.d(TAG, "Notification Title: ${it.title}")
            Log.d(TAG, "Notification Body: ${it.body}")
        }

        if (message.data.isNotEmpty()) {
            Log.d(TAG, "Data payload: ${message.data}")

            val event = message.data["event"]
            val sessionId = message.data["session_id"]
            val userId = message.data["user_id"]
            val success = message.data["success"] == "true"

            Log.d(TAG, "eKYC Result - event: $event, session: $sessionId, user: $userId, success: $success")

            if (event == "sign_up" || event == "sign_in") {
                val intent = Intent(EkycWaitingActivity.ACTION_EKYC_RESULT).apply {
                    putExtra(EkycWaitingActivity.EXTRA_EVENT, event)
                    putExtra(EkycWaitingActivity.EXTRA_SESSION_ID, sessionId)
                    putExtra(EkycWaitingActivity.EXTRA_SUCCESS, success)
                }
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            }
        }
    }
}

