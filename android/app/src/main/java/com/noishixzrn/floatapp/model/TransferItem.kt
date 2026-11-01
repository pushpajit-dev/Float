package com.noishixzrn.floatapp.model

data class TransferItem(
    val id: String,
    var name: String,
    val size: Long,
    val uriString: String?,
    val mimeType: String,
    val isPrivate: Boolean = false,
    val secretKey: String = "",
    val isOneTime: Boolean = false,
    val expiryTimeMs: Long = 0L,
    var downloadCount: Int = 0,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val isExpired: Boolean
        get() = expiryTimeMs > 0 && System.currentTimeMillis() > expiryTimeMs

    val category: String
        get() = when {
            mimeType.startsWith("image/") -> "image"
            mimeType.startsWith("video/") -> "video"
            mimeType.startsWith("audio/") -> "audio"
            mimeType.contains("html") || name.endsWith(".html", true) -> "html"
            mimeType.startsWith("text/") || mimeType.contains("pdf") || mimeType.contains("word") -> "doc"
            else -> "other"
        }
}
