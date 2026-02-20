package com.linh.banking_ekyc.activities

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.linh.banking_ekyc.R
import com.linh.banking_ekyc.adapter.TransactionAdapter
import com.linh.banking_ekyc.databinding.ActivityOverviewBinding
import com.linh.banking_ekyc.domain.ProfileModel
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Date
import java.util.Locale

class OverviewActivity : AppCompatActivity() {
    lateinit var binding: ActivityOverviewBinding
    private lateinit var profile: ProfileModel


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityOverviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bundle()
        setVariable()
        initChart()
        initLastTransaction()
    }

    private fun initLastTransaction() {
        binding.transactionList.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        binding.transactionList.adapter = TransactionAdapter(profile.transaction)
    }

    private fun bundle() {
        profile = intent.getSerializableExtra("object") as ProfileModel
    }

    private fun setVariable() {
        binding.backBtn.setOnClickListener {
            startActivity(Intent(this@OverviewActivity, BankingMainActivity::class.java))
        }
    }

    private fun getLast7DaysShortNames(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val today = LocalDate.now()
            (6 downTo 0).map { offset ->
                today.minusDays(offset.toLong())
                    .dayOfWeek
                    .getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
            }
        } else {
            val calendar = Calendar.getInstance()
            val format = SimpleDateFormat("EEE", Locale.ENGLISH)
            val days = mutableListOf<String>()
            for (i in 6 downTo 0) {
                calendar.time = Date()
                calendar.add(Calendar.DAY_OF_YEAR, -1)
                days.add(format.format(calendar.time))
            }
            days
        }
    }

    private fun initChart() {
        val values = listOf(150f, 200f, 50f, 100f, 250f, 80f, 300f)
        val entirs = values.mapIndexed { i, v -> BarEntry(i.toFloat(), v) }

        val barDataSet = BarDataSet(entirs, "Statistics").apply {
            setColors(
                intArrayOf(
                    R.color.chartOrange,
                    R.color.chartPink,
                    R.color.chartCyan
                ), this@OverviewActivity
            )
            setDrawValues(false)
        }

        val data = BarData(barDataSet).apply { barWidth = 0.7f }

        binding.barChart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            isEnabled = true
            this.data = data
        }

        val dayOfWeek = getLast7DaysShortNames()

        binding.barChart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            textColor = Color.WHITE
            setDrawGridLines(false)
            granularity = 1f
            labelCount = dayOfWeek.size
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String? {
                    return dayOfWeek.getOrNull(value.toInt()) ?: value.toString()
                }
            }
        }

        binding.barChart.axisRight.isEnabled = false
        binding.barChart.invalidate()
    }
}

