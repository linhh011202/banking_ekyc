package com.linh.banking_ekyc.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.linh.banking_ekyc.databinding.ActivitySignInBinding
import com.linh.banking_ekyc.network.LoginRequest
import com.linh.banking_ekyc.network.RetrofitClient
import com.linh.banking_ekyc.network.SessionManager
import kotlinx.coroutines.launch

class SignInActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySignInBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignInBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.signInBtn.setOnClickListener {
            val email = binding.emailEdt.text.toString().trim()
            val password = binding.passwordEdt.text.toString().trim()

            if (validateInput(email, password)) {
                signIn(email, password)
            }
        }

        binding.signUpTxt.setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }

        binding.faceLoginTxt.setOnClickListener {
            startActivity(Intent(this, EkycLoginEmailActivity::class.java))
        }
    }

    private fun validateInput(email: String, password: String): Boolean {
        var valid = true

        if (email.isEmpty()) {
            binding.emailLayout.error = "Email is required"
            valid = false
        } else {
            binding.emailLayout.error = null
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

        return valid
    }

    private fun signIn(email: String, password: String) {
        setLoading(true)

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.authApi.login(
                    LoginRequest(email = email, password = password)
                )

                setLoading(false)

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null && body.success && body.data != null) {
                        SessionManager.saveSession(
                            this@SignInActivity,
                            body.data.email,
                            body.data.accessToken
                        )
                        navigateToMain()
                    } else {
                        val errorMsg = body?.message ?: "Sign in failed"
                        Toast.makeText(this@SignInActivity, errorMsg, Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(
                        this@SignInActivity,
                        "Sign in failed: ${response.code()}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                setLoading(false)
                Toast.makeText(
                    this@SignInActivity,
                    "Network error: ${e.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, BankingMainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.signInBtn.visibility = if (loading) View.INVISIBLE else View.VISIBLE
        binding.emailEdt.isEnabled = !loading
        binding.passwordEdt.isEnabled = !loading
        binding.signUpTxt.isEnabled = !loading
        binding.faceLoginTxt.isEnabled = !loading
    }
}

