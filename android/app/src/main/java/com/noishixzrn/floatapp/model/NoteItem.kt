package com.noishixzrn.floatapp.model

data class NoteItem(
    val id: String,
    val sender: String,
    val text: String,
    val attachmentName: String? = null,
    val attachmentSize: Long = 0L,
    val attachmentToken: String? = null,
    val attachmentUri: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
