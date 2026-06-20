package dev.withcapsule.android.data.remote

import kotlinx.serialization.Serializable
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

@Serializable
data class FileStatus(
    val file_name: String = "Unknown",
    val file_size: Long = 0,
    val time_remaining: Long = 0,
    val is_encrypted: Boolean = false
)

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
    suspend fun getFileStatus(@Path("id") id: String): Response<FileStatus>

    @DELETE("delete/{id}")
    suspend fun deleteFile(@Path("id") id: String): Response<ResponseBody>
}
