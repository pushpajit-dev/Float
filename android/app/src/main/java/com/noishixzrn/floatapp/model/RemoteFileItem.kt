package com.noishixzrn.floatapp.model

data class RemoteFileItem(
    val name: String,
    val path: String,
    val size: Long,
    val isDirectory: Boolean,
    val mimeType: String = "",
    val category: String = "other", // "folder", "image", "video", "audio", "doc", "html", "other"
    val lastModified: Long = 0L,
    val downloadUrl: String? = null
)
