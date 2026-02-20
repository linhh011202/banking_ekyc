package com.linh.banking_ekyc.activities

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.linh.banking_ekyc.databinding.ActivityEkycWaitingBinding
import com.linh.banking_ekyc.network.SessionManager

class EkycWaitingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEkycWaitingBinding
    private var sessionId: String? = null
    private var flow: String? = null
    private var email: String? = null

    companion object {
        const val ACTION_EKYC_RESULT = "com.linh.banking_ekyc.EKYC_RESULT"
        const val EXTRA_EVENT = "event"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_SUCCESS = "success"
    }

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
        binding.sessionTxt.text = "Session: ${sessionId ?: "N/A"}"

        // Register for eKYC result broadcasts
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(ekycResultReceiver, IntentFilter(ACTION_EKYC_RESULT))
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this)
            .unregisterReceiver(ekycResultReceiver)
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
                // Save session for eKYC login flow
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
                if (flow == "ekyc_login") {
                    // Go back to eKYC login email screen
                    val intent = Intent(this, EkycLoginEmailActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                } else {
                    // Go back to sign up
                    val intent = Intent(this, SignUpActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                }
                finish()
            }
        }
    }
}
