package com.noishixzrn.floatapp.model

data class SessionData(
    val deviceName: String,
    val serverPort: Int,
    val autostart: Boolean,
    val knownDevices: List<DeviceItem>,
    val savedAt: Long = System.currentTimeMillis()
)
