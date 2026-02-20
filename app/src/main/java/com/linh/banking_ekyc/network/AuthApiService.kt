package com.linh.banking_ekyc.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface AuthApiService {

    @POST("api/v1/user/register")
    suspend fun register(@Body request: RegisterRequest): Response<ApiResponse<AuthData>>

    @POST("api/v1/user/login")
    suspend fun login(@Body request: LoginRequest): Response<ApiResponse<AuthData>>

    @Multipart
    @POST("api/v1/ekyc/upload-photos")
    suspend fun uploadPhotos(
        @Header("Authorization") authToken: String,
        @Part leftFaces: List<MultipartBody.Part>,
        @Part rightFaces: List<MultipartBody.Part>,
        @Part frontFaces: List<MultipartBody.Part>,
        @Part("fcm_token") fcmToken: RequestBody
    ): Response<ApiResponse<UploadPhotosData>>

    @Multipart
    @POST("api/v1/ekyc/login")
    suspend fun ekycLogin(
        @Part("email") email: RequestBody,
        @Part("fcm_token") fcmToken: RequestBody,
        @Part faces: List<MultipartBody.Part>
    ): Response<ApiResponse<EkycLoginData>>
}

