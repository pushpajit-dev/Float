package com.noishixzrn.floatapp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.noishixzrn.floatapp.databinding.ActivityCrashBinding
import java.io.File

class CrashActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCrashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCrashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val stackTrace = intent.getStringExtra(EXTRA_STACK_TRACE) ?: "No stack trace available"
        val reason = intent.getStringExtra(EXTRA_REASON) ?: "Unknown error"

        val phone = "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})"
        val androidVersion = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
        val kernel = getKernelVersion()

        binding.tvDeviceInfo.text = "Phone: $phone"
        binding.tvAndroidVersion.text = "Android Version: $androidVersion"
        binding.tvKernelVersion.text = "Kernel: $kernel"
        binding.tvCrashReason.text = "Reason: $reason"
        binding.tvStackTrace.text = stackTrace

        val fullReport = buildCrashReport(phone, androidVersion, kernel, reason, stackTrace)

        // Copy log button
        binding.btnCopyLog.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Float Crash Report", fullReport)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Crash log copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        // Report crash via email button
        binding.btnReportCrash.setOnClickListener {
            sendCrashEmail(fullReport)
        }

        // Restart app button
        binding.btnRestartApp.setOnClickListener {
            val restartIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(restartIntent)
            finish()
        }
    }

    private fun sendCrashEmail(report: String) {
        val subject = "App crashed"
        val mailtoUri = Uri.parse(
            "mailto:noishixzen@gmail.com?subject=" + Uri.encode(subject) + "&body=" + Uri.encode(report)
        )
        val mailIntent = Intent(Intent.ACTION_SENDTO, mailtoUri)
        try {
            startActivity(mailIntent)
        } catch (ignored: Exception) {
            val fallbackIntent = Intent(Intent.ACTION_SEND).apply {
                type = "message/rfc822"
                putExtra(Intent.EXTRA_EMAIL, arrayOf("noishixzen@gmail.com"))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, report)
            }
            try {
                startActivity(Intent.createChooser(fallbackIntent, "Send Crash Report"))
            } catch (e: Exception) {
                Toast.makeText(this, "No email client found: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        const val EXTRA_STACK_TRACE = "extra_stack_trace"
        const val EXTRA_REASON = "extra_reason"

        fun getKernelVersion(): String {
            return try {
                val proc = File("/proc/version")
                if (proc.exists() && proc.canRead()) {
                    proc.readText().trim()
                } else {
                    System.getProperty("os.version") ?: "Unknown"
                }
            } catch (ignored: Exception) {
                System.getProperty("os.version") ?: "Unknown"
            }
        }

        fun buildCrashReport(phone: String, androidVer: String, kernel: String, reason: String, stackTrace: String): String {
            return """Phone: $phone
Android version: $androidVer
Kernel: $kernel
Reason: $reason

--- Full Crash Log ---
$stackTrace"""
        }
    }
}
