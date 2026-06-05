package com.sean.capsule.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.android.bip39.Mnemonics
import com.sean.capsule.data.local.HistoryEntry
import com.sean.capsule.data.local.SettingsRepository
import com.sean.capsule.data.remote.ApiService
import com.sean.capsule.data.remote.RetrofitClient
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
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
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

class UploadViewModel(private val repository: SettingsRepository) : ViewModel() {
    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState: StateFlow<UploadState> = _uploadState.asStateFlow()

    private val _sharedFileUri = MutableStateFlow<Uri?>(null)
    val sharedFileUri: StateFlow<Uri?> = _sharedFileUri.asStateFlow()

    fun setSharedFileUri(uri: Uri?) {
        _sharedFileUri.value = uri
    }

    fun uploadFile(context: Context, baseUrl: String, fileUri: Uri, encrypt: Boolean) {
        viewModelScope.launch {
            try {
                val fileName = getFileName(context, fileUri) ?: "file"
                val originalSize = getFileSize(context, fileUri)

                var mnemonic: String? = null
                var tempEncryptedFile: File? = null

                val requestFile = if (encrypt) {
                    _uploadState.value = UploadState.Encrypting(0f)
                    val generatedMnemonic = Mnemonics.MnemonicCode(Mnemonics.WordCount.COUNT_12).words.joinToString(" ") { String(it) }
                    mnemonic = generatedMnemonic
                    
                    val tempFile = File(context.cacheDir, "upload_encrypted_${System.currentTimeMillis()}")
                    tempEncryptedFile = tempFile
                    
                    withContext(Dispatchers.IO) {
                        System.gc() // Try to free up memory before SCrypt allocation
                        val recipient = ScryptRecipient(generatedMnemonic.toByteArray())
                        
                        context.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                            FileOutputStream(tempFile).use { out ->
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
                                        if (originalSize > 0) {
                                            _uploadState.value = UploadState.Encrypting(bytesRead.toFloat() / originalSize)
                                        }
                                    }
                                    override fun close() = inputStream.close()
                                }
                                
                                Age.encryptStream(listOf<Recipient>(recipient), progressInputStream, out)
                            }
                        } ?: throw Exception("Failed to open input stream")
                    }

                    object : RequestBody() {
                        override fun contentType() = "application/octet-stream".toMediaTypeOrNull()
                        override fun contentLength() = tempFile.length()
                        override fun writeTo(sink: BufferedSink) {
                            tempFile.source().use { source ->
                                var totalWritten = 0L
                                val totalToUpload = tempFile.length()
                                var read: Long
                                while (source.read(sink.buffer, 8192).also { read = it } != -1L) {
                                    totalWritten += read
                                    _uploadState.value = UploadState.Uploading(totalWritten.toFloat() / totalToUpload)
                                }
                            }
                        }
                    }
                } else {
                    object : RequestBody() {
                        override fun contentType() = "application/octet-stream".toMediaTypeOrNull()
                        override fun contentLength() = originalSize
                        override fun writeTo(sink: BufferedSink) {
                            context.contentResolver.openInputStream(fileUri)?.use { input ->
                                input.source().use { source ->
                                    var totalWritten = 0L
                                    var read: Long
                                    while (source.read(sink.buffer, 8192).also { read = it } != -1L) {
                                        totalWritten += read
                                        _uploadState.value = UploadState.Uploading(totalWritten.toFloat() / originalSize)
                                    }
                                }
                            }
                        }
                    }
                }

                // Start Upload
                _uploadState.value = UploadState.Uploading(0f)
                val apiService = createApiService(baseUrl)
                
                val body = MultipartBody.Part.createFormData("f", fileName, requestFile)
                val response = apiService.uploadFile(encrypt, body)

                if (response.isSuccessful) {
                    val responseText = response.body()?.string() ?: ""
                    val fileId = extractFileId(responseText)
                    if (fileId != null) {
                        val downloadUrl = if (baseUrl.endsWith("/")) "${baseUrl}download/$fileId" else "$baseUrl/download/$fileId"
                        
                        repository.addHistoryEntry(
                            HistoryEntry(
                                id = fileId,
                                fileName = fileName,
                                timestamp = System.currentTimeMillis(),
                                isUpload = true,
                                isEncrypted = encrypt,
                                url = downloadUrl
                            )
                        )

                        _uploadState.value = UploadState.Success(fileId, downloadUrl, mnemonic)
                    } else {
                        _uploadState.value = UploadState.Error("Failed to parse file ID from response: $responseText")
                    }
                } else {
                    val error = response.errorBody()?.string() ?: "Upload failed: ${response.code()}"
                    _uploadState.value = UploadState.Error(error)
                }
                
                tempEncryptedFile?.delete()

            } catch (e: Exception) {
                e.printStackTrace()
                _uploadState.value = UploadState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun extractFileId(body: String): String? {
        val markers = listOf(
            "File ID for downloading is ",
            "File ID is "
        )
        
        for (marker in markers) {
            val start = body.indexOf(marker)
            if (start != -1) {
                val after = body.substring(start + marker.length).trim()
                val id = after.takeWhile { it.isLetterOrDigit() }
                if (id.isNotEmpty()) return id
            }
        }
        return null
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
        return RetrofitClient.getApiService(baseUrl)
    }

    fun resetState() {
        _uploadState.value = UploadState.Idle
    }
}
