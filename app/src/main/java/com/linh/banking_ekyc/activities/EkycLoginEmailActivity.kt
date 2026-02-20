package com.linh.banking_ekyc.activities

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import androidx.appcompat.app.AppCompatActivity
import com.linh.banking_ekyc.databinding.ActivityEkycLoginEmailBinding

class EkycLoginEmailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEkycLoginEmailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEkycLoginEmailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.backBtn.setOnClickListener {
            finish()
        }

        binding.signInTxt.setOnClickListener {
            finish()
        }

        binding.continueBtn.setOnClickListener {
            val email = binding.emailEdt.text.toString().trim()
            if (validateEmail(email)) {
                val intent = Intent(this, EkycLoginFaceScanActivity::class.java)
                intent.putExtra("email", email)
                startActivity(intent)
            }
        }
    }

    private fun validateEmail(email: String): Boolean {
        if (email.isEmpty()) {
            binding.emailLayout.error = "Email is required"
            return false
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailLayout.error = "Please enter a valid email"
            return false
        }
        binding.emailLayout.error = null
        return true
    }
}

