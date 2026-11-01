package com.noishixzrn.floatapp.utils

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Locale

object NetworkUtils {

    /** Returns the Wi-Fi IP address or falls back to first non-loopback IPv4 address. */
    @SuppressLint("WifiManagerLeak")
    fun getLocalIpAddress(context: Context): String {
        // Try Wi-Fi first
        val wifiMgr = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        wifiMgr?.connectionInfo?.ipAddress?.let { ipInt ->
            if (ipInt != 0) {
                return android.text.format.Formatter.formatIpAddress(ipInt)
            }
        }
        // Enumerate interfaces
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (_: Exception) {}
        return "127.0.0.1"
    }

    fun isWifiConnected(context: Context): Boolean {
        val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                as android.net.ConnectivityManager
        val nw = connMgr.activeNetwork ?: return false
        val caps = connMgr.getNetworkCapabilities(nw) ?: return false
        return caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
    }
}
