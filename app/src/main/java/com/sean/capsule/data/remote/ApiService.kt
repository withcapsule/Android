package com.sean.capsule.data.remote

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    @GET("ping")
    suspend fun ping(): String

    @Streaming
    @GET("download/{id}")
    suspend fun downloadFile(@Path("id") id: String): Response<ResponseBody>

    @Multipart
    @POST("curlup")
    suspend fun uploadFile(
        @Query("encrypted") encrypted: Boolean,
        @Part file: MultipartBody.Part
    ): Response<ResponseBody>

    @GET("status/{id}")
    suspend fun getFileStatus(@Path("id") id: String): Response<ResponseBody>

    @GET("delete/{id}")
    suspend fun deleteFile(@Path("id") id: String): Response<ResponseBody>
}
