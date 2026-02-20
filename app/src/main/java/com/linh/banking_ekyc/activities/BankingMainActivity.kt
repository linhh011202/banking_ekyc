package com.linh.banking_ekyc.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.linh.banking_ekyc.adapter.FriendsAdapter
import com.linh.banking_ekyc.databinding.ActivityBankingMainBinding
import com.linh.banking_ekyc.domain.Friend
import com.linh.banking_ekyc.domain.ProfileModel
import com.linh.banking_ekyc.domain.Transction
import com.linh.banking_ekyc.network.SessionManager

class BankingMainActivity : AppCompatActivity() {
    lateinit var binding: ActivityBankingMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityBankingMainBinding.inflate(layoutInflater)
        setContentView(binding.root)


        binding.logoutBtn.setOnClickListener {
            SessionManager.clearSession(this)
            val intent = Intent(this, SignInActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        loadProfile()
    }

    private fun loadProfile() {
        Log.d("BankingMain", "loadProfile() called")
        // Force scroll view visible in case it was hidden
        binding.scrollView2.visibility = View.VISIBLE
        binding.progressBar.visibility = View.GONE
        val email = SessionManager.getEmail(this) ?: "User"
        val displayName = email.substringBefore("@").replaceFirstChar { it.uppercase() }

        val profile = ProfileModel(
            profileName = displayName,
            totalbalance = "$12,500.00",
            income = "$8,200.00",
            outcome = "$4,300.00",
            friend = arrayListOf(
                Friend(name = "Alice"),
                Friend(name = "Bob"),
                Friend(name = "Charlie"),
                Friend(name = "Diana")
            ),
            transaction = arrayListOf(
                Transction(name = "Netflix", data = "Feb 20, 2026", amount = "-$15.99"),
                Transction(name = "Salary", data = "Feb 15, 2026", amount = "+$5,000.00"),
                Transction(name = "Grocery", data = "Feb 14, 2026", amount = "-$85.50")
            )
        )

        binding.nameTxt.text = profile.profileName
        binding.totalTxt.text = profile.totalbalance
        binding.incomeTxt.text = profile.income
        binding.outcomeTxt.text = profile.outcome

        binding.friendsList.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.friendsList.adapter = FriendsAdapter(profile.friend)

        binding.detailLayout.setOnClickListener {
            val intent = Intent(this, OverviewActivity::class.java)
            intent.putExtra("object", profile)
            startActivity(intent)
        }
    }
}

