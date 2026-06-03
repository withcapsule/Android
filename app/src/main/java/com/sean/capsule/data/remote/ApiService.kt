package com.sean.capsule.data.remote

import retrofit2.http.GET

interface ApiService {
    @GET("ping")
    suspend fun ping(): String
}
