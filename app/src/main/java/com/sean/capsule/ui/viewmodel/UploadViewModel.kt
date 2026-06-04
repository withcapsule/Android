package com.sean.capsule.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.android.bip39.Mnemonics
import com.sean.capsule.data.remote.ApiService
import kage.Age
import kage.Recipient
import kage.crypto.scrypt.ScryptRecipient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.logging.HttpLoggingInterceptor
import okio.BufferedSink
import okio.source
import retrofit2.Retrofit
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

sealed class UploadState {
    object Idle : UploadState()
    data class Encrypting(val progress: Float) : UploadState()
    data class Uploading(val progress: Float) : UploadState()
    data class Success(val fileId: String, val downloadUrl: String, val mnemonic: String? = null) : UploadState()
    data class Error(val message: String) : UploadState()
}

class UploadViewModel : ViewModel() {
    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState: StateFlow<UploadState> = _uploadState.asStateFlow()

    fun uploadFile(context: Context, baseUrl: String, fileUri: Uri, encrypt: Boolean) {
        viewModelScope.launch {
            try {
                val fileName = getFileName(context, fileUri) ?: "file"
                val totalSize = getFileSize(context, fileUri)

                var uploadFile: File
                var mnemonic: String? = null

                if (encrypt) {
                    _uploadState.value = UploadState.Encrypting(0f)
                    val generatedMnemonic = Mnemonics.MnemonicCode(Mnemonics.WordCount.COUNT_12).words.joinToString(" ") { String(it) }
                    mnemonic = generatedMnemonic
                    
                    val tempEncryptedFile = File(context.cacheDir, "upload_encrypted_${System.currentTimeMillis()}")
                    
                    withContext(Dispatchers.IO) {
                        System.gc() // Try to free up memory before SCrypt allocation
                        val recipient = ScryptRecipient(generatedMnemonic.toByteArray())
                        
                        context.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                            FileOutputStream(tempEncryptedFile).use { out ->
                                // Track encryption progress by wrapping input stream
                                val progressInputStream = object : InputStream() {
                                    private var bytesRead = 0L
                                    override fun read(): Int {
                                        val byte = inputStream.read()
                                        if (byte != -1) updateProgress(1)
                                        return byte
                                    }
                                    override fun read(b: ByteArray, off: Int, len: Int): Int {
                                        val count = inputStream.read(b, off, len)
                                        if (count != -1) updateProgress(count.toLong())
                                        return count
                                    }
                                    private fun updateProgress(count: Long) {
                                        bytesRead += count
                                        if (totalSize > 0) {
                                            _uploadState.value = UploadState.Encrypting(bytesRead.toFloat() / totalSize)
                                        }
                                    }
                                    override fun close() = inputStream.close()
                                }
                                
                                Age.encryptStream(listOf<Recipient>(recipient), progressInputStream, out)
                            }
                        } ?: throw Exception("Failed to open input stream")
                    }
                    uploadFile = tempEncryptedFile
                } else {
                    // Copy to temp file for upload consistency
                    val tempFile = File(context.cacheDir, "upload_temp_${System.currentTimeMillis()}")
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(fileUri)?.use { input ->
                            FileOutputStream(tempFile).use { output ->
                                input.copyTo(output)
                            }
                        } ?: throw Exception("Failed to open input stream")
                    }
                    uploadFile = tempFile
                }

                // Start Upload
                _uploadState.value = UploadState.Uploading(0f)
                val apiService = createApiService(baseUrl)
                
                val requestFile = object : RequestBody() {
                    override fun contentType() = "application/octet-stream".toMediaTypeOrNull()
                    override fun contentLength() = uploadFile.length()
                    override fun writeTo(sink: BufferedSink) {
                        uploadFile.source().use { source ->
                            var totalWritten = 0L
                            var read: Long
                            while (source.read(sink.buffer, 8192).also { read = it } != -1L) {
                                totalWritten += read
                                _uploadState.value = UploadState.Uploading(totalWritten.toFloat() / uploadFile.length())
                            }
                        }
                    }
                }

                val body = MultipartBody.Part.createFormData("f", fileName, requestFile)
                val response = apiService.uploadFile(encrypt, body)

                if (response.isSuccessful) {
                    val responseText = response.body()?.string() ?: ""
                    val fileId = extractFileId(responseText)
                    if (fileId != null) {
                        val downloadUrl = if (baseUrl.endsWith("/")) "${baseUrl}download/$fileId" else "$baseUrl/download/$fileId"
                        _uploadState.value = UploadState.Success(fileId, downloadUrl, mnemonic)
                    } else {
                        _uploadState.value = UploadState.Error("Failed to parse file ID from response: $responseText")
                    }
                } else {
                    _uploadState.value = UploadState.Error("Upload failed: ${response.code()}")
                }
                
                uploadFile.delete()

            } catch (e: Exception) {
                e.printStackTrace()
                _uploadState.value = UploadState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun extractFileId(body: String): String? {
        val marker = "File ID for downloading is "
        val start = body.indexOf(marker)
        if (start == -1) return null
        val after = body.substring(start + marker.length)
        return after.takeWhile { it.isLetterOrDigit() }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) name = cursor.getString(index)
            }
        }
        return name ?: uri.path?.substringAfterLast('/')
    }

    private fun getFileSize(context: Context, uri: Uri): Long {
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (index != -1) size = cursor.getLong(index)
            }
        }
        return size
    }

    private fun createApiService(baseUrl: String): ApiService {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.HEADERS
                }
            )
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl.let { if (it.endsWith("/")) it else "$it/" })
            .client(okHttpClient)
            .build()

        return retrofit.create(ApiService::class.java)
    }

    fun resetState() {
        _uploadState.value = UploadState.Idle
    }
}
