package dev.withcapsule.android.data.remote

import kotlinx.serialization.Serializable
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

@Serializable
data class FileStatus(
    val file_name: String = "Unknown",
    val file_size: Long = 0,
    val upload_time: Long = 0,
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
    @POST("upload")
    suspend fun uploadFile(
        @Query("encrypted") encrypted: Boolean,
        @Part file: MultipartBody.Part
    ): Response<ResponseBody>

    @GET("status/{id}")
    suspend fun getFileStatus(@Path("id") id: String): Response<FileStatus>

    @DELETE("delete/{id}")
    suspend fun deleteFile(@Path("id") id: String): Response<ResponseBody>
}
