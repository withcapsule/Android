package dev.withcapsule.android.ui.viewmodel

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName

import dev.withcapsule.android.ui.viewmodel.DownloadViewModel.Companion.sanitizeFileName;

class FilenameSanitizationTest {
    @Test
    @DisplayName("Should extract and clean standard filenames")
    fun sanitizeFileName_validNames() {
        assertEquals("photo.jpg", sanitizeFileName("photo.jpg"))
        assertEquals("my_document_2026.pdf", sanitizeFileName("my_document_2026.pdf"))
    }

    @Test
    @DisplayName("Should properly split mixed forward and backward slashes")
    fun sanitizeFileName_mixedSlashes() {
        assertEquals("file.txt", sanitizeFileName("C:\\folder/subfolder/file.txt"))
        assertEquals("malicious.exe", sanitizeFileName("/uploads\\malicious.exe"))
    }

    @Test
    @DisplayName("Should strip forbidden Windows characters")
    fun sanitizeFileName_illegalCharacters() {
        assertEquals("filename.txt", sanitizeFileName("file*name?.txt"))
    }

    @Test
    @DisplayName("Should block directory traversal attacks and empty inputs")
    fun sanitizeFileName_directoryTraversal() {
        assertNull( sanitizeFileName("../../etc/passwd") )
        assertNull( sanitizeFileName("..") )
        assertNull( sanitizeFileName("   ") )
    }

    @Test
    @DisplayName("Should safely restrict a file name to 255 characters")
    fun sanitizeFileName_oversizedName() {
        val longString = "a".repeat(300)
        val result = sanitizeFileName(longString)
        assertEquals(255, result?.length)
    }
}