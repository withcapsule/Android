package com.sean.capsule.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Streaming

interface ApiService {
    @GET("ping")
    suspend fun ping(): String

    @Streaming
    @GET("download/{id}")
    suspend fun downloadFile(@Path("id") id: String): Response<ResponseBody>
}
