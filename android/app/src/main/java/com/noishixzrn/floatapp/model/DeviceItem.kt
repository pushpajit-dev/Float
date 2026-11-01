package com.noishixzrn.floatapp.model

data class DeviceItem(
    val id: String,
    val name: String,
    val hardwareTrace: String,
    val host: String,
    val port: Int = 8080,
    val isOnline: Boolean = true,
    var lastSeen: Long = System.currentTimeMillis()
) {
    val endpointUrl: String
        get() = if (host.startsWith("http://") || host.startsWith("https://")) {
            host
        } else {
            "http://$host:$port"
        }
}
