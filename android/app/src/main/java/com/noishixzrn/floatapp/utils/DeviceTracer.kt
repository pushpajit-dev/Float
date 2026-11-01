package com.noishixzrn.floatapp.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest
import java.util.UUID

object DeviceTracer {

    private const val PREF_NAME = "float_device"
    private const val KEY_TRACE = "hw_trace"
    private const val KEY_UUID = "device_uuid"

    /**
     * Returns a stable hardware trace string like "HW-TR-A4F2-8B1C".
     * Generated once per install from device fingerprint, stored in SharedPreferences.
     */
    fun getHardwareTrace(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_TRACE, null)
        if (existing != null) return existing

        // Build a fingerprint from stable device info
        val fingerprint = buildString {
            append(Build.BRAND).append(Build.MODEL).append(Build.BOARD)
            append(Build.DEVICE).append(Build.MANUFACTURER)
            append(Build.HARDWARE).append(Build.PRODUCT)
        }

        val raw = try {
            val md = MessageDigest.getInstance("SHA-256")
            val hash = md.digest(fingerprint.toByteArray())
            hash.fold("") { str, b -> str + "%02x".format(b) }
        } catch (_: Exception) {
            UUID.randomUUID().toString().replace("-", "")
        }

        // Take 8 hex chars and format as HW-TR-XXXX-XXXX
        val part1 = raw.substring(0, 4).uppercase()
        val part2 = raw.substring(4, 8).uppercase()
        val trace = "HW-TR-$part1-$part2"

        prefs.edit().putString(KEY_TRACE, trace).apply()
        return trace
    }

    /** Friendly device label, e.g. "Samsung Galaxy S24 (Float Mobile)" */
    fun getDeviceLabel(): String {
        val brand = Build.BRAND.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        return if (model.startsWith(brand, ignoreCase = true)) "$model (Float Mobile)"
        else "$brand $model (Float Mobile)"
    }

    /** Stable UUID per install, used as a secondary ID. */
    fun getDeviceUuid(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_UUID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_UUID, it).apply()
        }
    }
}
