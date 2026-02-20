package com.linh.banking_ekyc.network

import com.google.gson.annotations.SerializedName

// ==================== Request Models ====================

data class RegisterRequest(
    val email: String,
    val password: String,
    @SerializedName("full_name")
    val fullName: String,
    @SerializedName("phone_number")
    val phoneNumber: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

// ==================== Response Models ====================

data class ApiResponse<T>(
    val success: Boolean,
    val code: Int,
    val message: String,
    val data: T?
)

data class AuthData(
    val email: String,
    @SerializedName("access_token")
    val accessToken: String,
    @SerializedName("token_type")
    val tokenType: String
)

data class UploadPhotosData(
    @SerializedName("session_id")
    val sessionId: String
)

data class EkycLoginData(
    @SerializedName("session_id")
    val sessionId: String
)

