package dev.withcapsule.android.ui.viewmodel

import android.content.ContentValues
import android.webkit.MimeTypeMap
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.withcapsule.android.data.local.HistoryEntry
import dev.withcapsule.android.data.local.SettingsRepository
import dev.withcapsule.android.data.remote.ApiService
import dev.withcapsule.android.data.remote.RetrofitClient
import kage.Age
import kage.Identity
import kage.crypto.scrypt.ScryptIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Float) : DownloadState()
    data class Decrypting(val tempFile: File, val suggestedName: String, val fileId: String, val progress: Float = 0f) : DownloadState()
    data class Success(val uri: Uri, val fileName: String) : DownloadState()
    data class Error(val message: String) : DownloadState()
}

class DownloadViewModel(private val repository: SettingsRepository) : ViewModel() {
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val _pendingDownloadUrl = MutableStateFlow<String?>(null)
    val pendingDownloadUrl: StateFlow<String?> = _pendingDownloadUrl.asStateFlow()

    fun setPendingDownloadUrl(url: String?) {
        _pendingDownloadUrl.value = url
    }

    fun startDownload(context: Context, baseUrl: String, idOrUrl: String, downloadDirUri: String?) {
        val id = extractId(idOrUrl)
        
        viewModelScope.launch {
            _downloadState.value = DownloadState.Downloading(0f)
            
            try {
                val apiService = createApiService(baseUrl)
                val response = apiService.downloadFile(id)
                
                if (response.isSuccessful) {
                    val body = response.body() ?: throw Exception("Empty response body")
                    val totalBytes = body.contentLength()
                    val isEncrypted = response.headers()["X-Encrypted"]?.equals("true", ignoreCase = true) ?: false
                    val contentDisposition = response.headers()["Content-Disposition"]
                    val fileName = parseFileName(contentDisposition) ?: "downloaded_file"
                    
                    val tempFile = File(context.cacheDir, "temp_download_${System.currentTimeMillis()}")
                    
                    saveToFileWithProgress(body.byteStream(), tempFile, totalBytes) { progress ->
                        _downloadState.value = DownloadState.Downloading(progress)
                    }
                    
                    if (isEncrypted) {
                        _downloadState.value = DownloadState.Decrypting(tempFile, fileName, id, 0f)
                    } else {
                        val finalUri = saveToFinalLocation(context, tempFile, fileName, downloadDirUri)
                        if (finalUri != null) {
                            repository.addHistoryEntry(
                                HistoryEntry(
                                    id = id,
                                    fileName = fileName,
                                    timestamp = System.currentTimeMillis(),
                                    isUpload = false,
                                    isEncrypted = false
                                )
                            )
                            _downloadState.value = DownloadState.Success(finalUri, fileName)
                        } else {
                            _downloadState.value = DownloadState.Error("Failed to save file to destination")
                        }
                    }
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                    _downloadState.value = DownloadState.Error("Server error (${response.code()}): $errorBody")
                }
            } catch (e: Exception) {
                _downloadState.value = DownloadState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun decryptAndSave(context: Context, tempFile: File, fileName: String, fileId: String, mnemonic: String, downloadDirUri: String?) {
        viewModelScope.launch {
            _downloadState.value = DownloadState.Decrypting(tempFile, fileName, fileId, 0f)
            
            try {
                val normalizedMnemonic = mnemonic.trim().replace("\\s+".toRegex(), " ")
                
                val decryptedFile = withContext(Dispatchers.IO) {
                    System.gc() // Try to free up memory before SCrypt allocation
                    val identity = ScryptIdentity(normalizedMnemonic.toByteArray())
                    val totalSize = tempFile.length()
                    val out = File(context.cacheDir, "decrypted_${System.currentTimeMillis()}")
                    
                    // Tracking progress by wrapping the InputStream instead of OutputStream
                    tempFile.inputStream().use { fileInputStream ->
                        val progressInputStream = object : InputStream() {
                            private var bytesRead = 0L
                            override fun read(): Int {
                                val byte = fileInputStream.read()
                                if (byte != -1) updateProgress(1)
                                return byte
                            }
                            override fun read(b: ByteArray, off: Int, len: Int): Int {
                                val count = fileInputStream.read(b, off, len)
                                if (count != -1) updateProgress(count.toLong())
                                return count
                            }
                            private fun updateProgress(count: Long) {
                                bytesRead += count
                                if (totalSize > 0) {
                                    val progress = (bytesRead.toFloat() / totalSize).coerceIn(0f, 1f)
                                    _downloadState.value = DownloadState.Decrypting(tempFile, fileName, fileId, progress)
                                }
                            }
                            override fun close() = fileInputStream.close()
                        }

                        FileOutputStream(out).use { outputStream ->
                            Age.decryptStream(listOf<Identity>(identity), progressInputStream, outputStream)
                        }
                    }
                    out
                }
                
                val finalUri = saveToFinalLocation(context, decryptedFile, fileName, downloadDirUri)
                tempFile.delete()
                if (finalUri != null) {
                    repository.addHistoryEntry(
                        HistoryEntry(
                            id = fileId,
                            fileName = fileName,
                            timestamp = System.currentTimeMillis(),
                            isUpload = false,
                            isEncrypted = true
                        )
                    )
                    _downloadState.value = DownloadState.Success(finalUri, fileName)
                } else {
                    _downloadState.value = DownloadState.Error("Failed to save decrypted file")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _downloadState.value = DownloadState.Error("Decryption failed: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    private fun extractId(input: String): String {
        return if (input.contains("/download/")) {
            input.substringAfter("/download/").substringBefore("/").substringBefore("?")
        } else {
            input
        }
    }

    private fun createApiService(baseUrl: String): ApiService {
        return RetrofitClient.getApiService(baseUrl)
    }

    private suspend fun saveToFileWithProgress(
        inputStream: InputStream, 
        file: File, 
        totalBytes: Long,
        onProgress: (Float) -> Unit
    ) = withContext(Dispatchers.IO) {
        inputStream.use { input ->
            FileOutputStream(file).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalRead = 0L
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    if (totalBytes > 0) {
                        onProgress(totalRead.toFloat() / totalBytes)
                    }
                }
            }
        }
    }

    private suspend fun saveToFinalLocation(
        context: Context, 
        sourceFile: File, 
        fileName: String, 
        downloadDirUri: String?
    ): Uri? = withContext(Dispatchers.IO) {
        if (downloadDirUri == null) {
            saveUsingMediaStore(context, sourceFile, fileName)
        } else {
            saveUsingSAF(context, sourceFile, fileName, downloadDirUri)
        }
    }

    private fun mimeTypeFor(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    private fun saveUsingMediaStore(context: Context, sourceFile: File, fileName: String): Uri? {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeTypeFor(fileName))
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Capsules")
        }

        val contentResolver = context.contentResolver
        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        
        return uri?.also { targetUri ->
            contentResolver.openOutputStream(targetUri)?.use { output ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            }
            sourceFile.delete()
        }
    }

    private fun saveUsingSAF(context: Context, sourceFile: File, fileName: String, treeUriStr: String): Uri? {
        val treeUri = treeUriStr.toUri()
        
        val pickedDir = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        if (!pickedDir.canWrite()) return null
        
        val existingFile = pickedDir.findFile(fileName)
        existingFile?.delete()
        
        val newFile = pickedDir.createFile(mimeTypeFor(fileName), fileName) ?: return null
        
        return newFile.uri.also { targetUri ->
            context.contentResolver.openOutputStream(targetUri)?.use { output ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            }
            sourceFile.delete()
        }
    }

    private fun parseFileName(contentDisposition: String?): String? {
        if (contentDisposition == null) return null
        return contentDisposition.split(";").find { it.trim().startsWith("filename=") }
            ?.substringAfter("filename=")
            ?.trim()
            ?.removeSurrounding("\"")
    }
    
    fun resetState() {
        _downloadState.value = DownloadState.Idle
    }
}
