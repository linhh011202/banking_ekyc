package com.linh.banking_ekyc.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.linh.banking_ekyc.databinding.ActivitySignUpBinding
import com.linh.banking_ekyc.network.RegisterRequest
import com.linh.banking_ekyc.network.RetrofitClient
import com.linh.banking_ekyc.network.SessionManager
import kotlinx.coroutines.launch

class SignUpActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySignUpBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignUpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.signUpBtn.setOnClickListener {
            val name = binding.nameEdt.text.toString().trim()
            val email = binding.emailEdt.text.toString().trim()
            val phone = binding.phoneEdt.text.toString().trim()
            val password = binding.passwordEdt.text.toString().trim()
            val confirmPassword = binding.confirmPasswordEdt.text.toString().trim()

            if (validateInput(name, email, phone, password, confirmPassword)) {
                signUp(name, email, phone, password)
            }
        }

        binding.signInTxt.setOnClickListener {
            finish()
        }

        binding.backBtn.setOnClickListener {
            finish()
        }
    }

    private fun validateInput(
        name: String,
        email: String,
        phone: String,
        password: String,
        confirmPassword: String
    ): Boolean {
        var valid = true

        if (name.isEmpty()) {
            binding.nameLayout.error = "Name is required"
            valid = false
        } else {
            binding.nameLayout.error = null
        }

        if (email.isEmpty()) {
            binding.emailLayout.error = "Email is required"
            valid = false
        } else {
            binding.emailLayout.error = null
        }

        if (phone.isEmpty()) {
            binding.phoneLayout.error = "Phone number is required"
            valid = false
        } else {
            binding.phoneLayout.error = null
        }

        if (password.isEmpty()) {
            binding.passwordLayout.error = "Password is required"
            valid = false
        } else if (password.length < 6) {
            binding.passwordLayout.error = "Password must be at least 6 characters"
            valid = false
        } else {
            binding.passwordLayout.error = null
        }

        if (confirmPassword.isEmpty()) {
            binding.confirmPasswordLayout.error = "Please confirm your password"
            valid = false
        } else if (confirmPassword != password) {
            binding.confirmPasswordLayout.error = "Passwords do not match"
            valid = false
        } else {
            binding.confirmPasswordLayout.error = null
        }

        return valid
    }

    private fun signUp(name: String, email: String, phone: String, password: String) {
        setLoading(true)

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.authApi.register(
                    RegisterRequest(
                        email = email,
                        password = password,
                        fullName = name,
                        phoneNumber = phone
                    )
                )

                setLoading(false)

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null && body.success && body.data != null) {
                        SessionManager.saveSession(
                            this@SignUpActivity,
                            body.data.email,
                            body.data.accessToken
                        )
                        Toast.makeText(this@SignUpActivity, "Account created successfully!", Toast.LENGTH_SHORT).show()
                        navigateToFaceScan()
                    } else {
                        val errorMsg = body?.message ?: "Sign up failed"
                        Toast.makeText(this@SignUpActivity, errorMsg, Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(
                        this@SignUpActivity,
                        "Sign up failed: ${response.code()}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                setLoading(false)
                Toast.makeText(
                    this@SignUpActivity,
                    "Network error: ${e.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun navigateToFaceScan() {
        val intent = Intent(this, FaceScanActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.signUpBtn.visibility = if (loading) View.INVISIBLE else View.VISIBLE
        binding.nameEdt.isEnabled = !loading
        binding.emailEdt.isEnabled = !loading
        binding.phoneEdt.isEnabled = !loading
        binding.passwordEdt.isEnabled = !loading
        binding.confirmPasswordEdt.isEnabled = !loading
        binding.signInTxt.isEnabled = !loading
        binding.backBtn.isEnabled = !loading
    }
}

