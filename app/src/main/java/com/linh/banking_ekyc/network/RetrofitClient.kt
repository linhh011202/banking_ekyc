package com.linh.banking_ekyc.network

import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private const val TAG = "RetrofitClient"
    private const val BASE_URL = "https://api.endpoints.banking-ekyc-487718.cloud.goog/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
        .dispatcher(Dispatcher().apply {
            maxRequests = 10
            maxRequestsPerHost = 10
        })
        .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val authApi: AuthApiService = retrofit.create(AuthApiService::class.java)

    /**
     * Pre-warm the connection to the server by making a lightweight HEAD request.
     * This establishes TCP + TLS ahead of time so that the actual upload reuses
     * the warm connection from the pool (~300-1000ms saved).
     *
     * Call this when entering the face scan screen — by the time the user finishes
     * scanning, the connection will be ready.
     */
    fun prewarmConnection() {
        val request = Request.Builder()
            .url(BASE_URL)
            .head()
            .build()

        okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                response.close()
                Log.d(TAG, "Connection pre-warmed (${response.protocol})")
            }

            override fun onFailure(call: Call, e: IOException) {
                Log.d(TAG, "Pre-warm failed (non-critical): ${e.message}")
            }
        })
    }
}

