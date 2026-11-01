package com.noishixzrn.floatapp.utils

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.InputStream

object FileUtils {

    fun getFileName(contentResolver: ContentResolver, uri: Uri): String {
        var name = "file_${System.currentTimeMillis()}"
        val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (it.moveToFirst() && nameIndex >= 0) {
                name = it.getString(nameIndex) ?: name
            }
        }
        return name
    }

    fun getFileSize(contentResolver: ContentResolver, uri: Uri): Long {
        var size = 0L
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
            if (it.moveToFirst() && sizeIndex >= 0) {
                size = it.getLong(sizeIndex)
            }
        }
        return size
    }

    fun getMimeType(contentResolver: ContentResolver, uri: Uri): String {
        return contentResolver.getType(uri) ?: "application/octet-stream"
    }

    fun formatFileSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
        else -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    }

    fun readBytes(contentResolver: ContentResolver, uri: Uri): ByteArray? {
        return try {
            contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            null
        }
    }

    /** Returns file category chip tag based on mime type. */
    fun getCategoryTag(mimeType: String): String = when {
        mimeType.startsWith("image/") -> "images"
        mimeType.startsWith("video/") -> "videos"
        mimeType.startsWith("audio/") -> "audio"
        mimeType == "application/vnd.android.package-archive" -> "apks"
        mimeType.startsWith("application/pdf") ||
        mimeType.startsWith("application/msword") ||
        mimeType.startsWith("application/vnd.openxmlformats") ||
        mimeType.startsWith("text/") -> "docs"
        else -> "all"
    }

    /** Generates a MIME type string for the picker intent for a given chip selection. */
    fun getMimeForCategory(category: String): Array<String> = when (category) {
        "images" -> arrayOf("image/*")
        "videos" -> arrayOf("video/*")
        "audio" -> arrayOf("audio/*")
        "docs" -> arrayOf("application/pdf", "text/*", "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
        "apks" -> arrayOf("application/vnd.android.package-archive")
        else -> arrayOf("*/*")
    }
}
