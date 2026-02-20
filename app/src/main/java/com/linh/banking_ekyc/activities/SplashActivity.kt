package com.linh.banking_ekyc.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.linh.banking_ekyc.network.SessionManager
import com.linh.banking_ekyc.databinding.ActivitySplashBinding

class SplashActivity : AppCompatActivity() {
    lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Skip auto-login for now - always show splash
        // TODO: re-enable after UI is confirmed working
        // val isLoggedIn = SessionManager.isLoggedIn(this)
        val isLoggedIn = false
        Log.d("SplashActivity", "isLoggedIn=$isLoggedIn (forced false), email=${SessionManager.getEmail(this)}")
        if (isLoggedIn) {
            startActivity(Intent(this, BankingMainActivity::class.java))
            finish()
            return
        }

        binding.startBtn.setOnClickListener {
            startActivity(Intent(this, SignInActivity::class.java))
        }

    }
}

