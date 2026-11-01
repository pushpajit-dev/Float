package com.noishixzrn.floatapp.server

import android.content.Context
import android.util.Log
import com.jcraft.jsch.Channel
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import com.jcraft.jsch.Session
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Collections
import kotlin.concurrent.thread

/**
 * Modern online tunnel manager for Float Android.
 * 1. Primary Engine: Pure-Java SSH reverse tunneling via Pinggy (a.pinggy.io:443)
 *    and Localhost.run (localhost.run:22).
 *    - 100% pure Java inside the Android JVM (Zero native .so binaries needed).
 *    - Fully compatible with Android 8 through 15 (No 16KB page-size or SELinux W^X issues).
 *    - Operates over standard TCP 443 (HTTPS), bypassing telecom / ISP blocks.
 * 2. Headless Engine: Cloudflare Zero Trust Named Tunnel (ready in code for future token activation).
 */
class TunnelManager(private val context: Context) {
    private val TAG = "TunnelManager"

    @Volatile var isActive = false
    @Volatile var isStarting = false
    @Volatile var tunnelUrl: String = ""

    // SSH Session & Channel references
    private var sshSession: Session? = null
    private var sshChannel: Channel? = null
    private var workerThread: Thread? = null

    // Headless Cloudflare Zero Trust process reference
    private var cfProcess: Process? = null

    private fun ensureJSchKey(jsch: JSch) {
        try {
            val keyPair = KeyPair.genKeyPair(jsch, KeyPair.RSA, 2048)
            val privStream = ByteArrayOutputStream()
            keyPair.writePrivateKey(privStream)
            val pubStream = ByteArrayOutputStream()
            keyPair.writePublicKey(pubStream, "float-android")
            jsch.addIdentity("float-key", privStream.toByteArray(), pubStream.toByteArray(), null)
        } catch (e: Exception) {
            Log.w(TAG, "Error generating JSch key: ${e.message}")
        }
    }

    fun startTunnel(
        localPort: Int,
        onSettingUp: (status: String) -> Unit = {},
        onReady: (url: String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (isActive && tunnelUrl.isNotEmpty()) {
            onReady(tunnelUrl)
            return
        }

        stopTunnel()
        isStarting = true
        onSettingUp("Connecting...")

        workerThread = thread(name = "Float-OnlineTunnel") {
            try {
                // Step 1: Attempt Pinggy over port 443
                Log.i(TAG, "Attempting Pinggy tunnel on port 443 for local port $localPort")
                onSettingUp("Connecting to Pinggy (Port 443)...")

                val pinggySuccess = tryConnectPinggy(localPort, onSettingUp, onReady)
                if (pinggySuccess) {
                    return@thread
                }

                if (!isStarting) return@thread

                // Step 2: Instant Fallback to Localhost.run
                Log.i(TAG, "Pinggy did not yield URL, falling back to Localhost.run")
                onSettingUp("Connecting to Secure Tunnel...")
                val lhrSuccess = tryConnectLocalhostRun(localPort, onSettingUp, onReady)
                if (lhrSuccess) {
                    return@thread
                }

                if (!isStarting) return@thread

                // If both fail:
                isStarting = false
                isActive = false
                tunnelUrl = ""
                onError("Unable to establish online tunnel. Please check internet connection.")
            } catch (e: Exception) {
                Log.e(TAG, "Tunnel startup exception: ${e.message}", e)
                isStarting = false
                isActive = false
                tunnelUrl = ""
                onError(e.message ?: "Failed to start online tunnel")
            }
        }
    }

    private fun tryConnectPinggy(
        localPort: Int,
        onSettingUp: (String) -> Unit,
        onReady: (String) -> Unit
    ): Boolean {
        try {
            val jsch = JSch()
            ensureJSchKey(jsch)
            val session = jsch.getSession("a", "free.pinggy.io", 443)
            session.setConfig("StrictHostKeyChecking", "no")
            session.setConfig("ServerAliveInterval", "30")
            session.timeout = 6000
            session.connect(6000)
            sshSession = session

            // Set remote port forwarding: port 0 = server chooses random port
            session.setPortForwardingR(0, "127.0.0.1", localPort)
            Log.i(TAG, "Pinggy port forwarding established")
            onSettingUp("Allocating Online domain...")

            // Open shell channel with PTY
            val channel = session.openChannel("shell") as ChannelShell
            channel.setPty(true)
            channel.setPtyType("vt100")
            channel.connect(5000)
            sshChannel = channel

            val inStream = channel.inputStream
            val accumulator = StringBuilder()
            val lock = Object()
            var detectedUrl: String? = null

            val readerThread = thread(name = "Float-Pinggy-Reader") {
                val buf = ByteArray(1024)
                try {
                    while (channel.isConnected && detectedUrl == null && isStarting) {
                        val count = inStream.read(buf)
                        if (count == -1) break
                        val text = String(buf, 0, count)
                        synchronized(lock) {
                            accumulator.append(text)
                            val match = Regex("""https?://[a-zA-Z0-9-]+\.(?:a\.)?pinggy\.(?:link|io)[^\s"'<>|]*""").find(accumulator)
                                ?: Regex("""https?://[a-zA-Z0-9-]+\.lhr\.(?:life|rocks)[^\s"'<>|]*""").find(accumulator)
                            if (match != null) {
                                detectedUrl = match.value.trim().trimEnd('/', '|', '\\', ' ', '\t', '\r', '\n')
                            }
                        }
                        if (detectedUrl != null) break
                    }
                } catch (_: Exception) {}
            }

            val deadline = System.currentTimeMillis() + 5000 // 5 seconds max for Pinggy response
            while (System.currentTimeMillis() < deadline && detectedUrl == null && channel.isConnected && isStarting) {
                Thread.sleep(100)
            }

            val resultUrl = detectedUrl
            if (resultUrl != null) {
                tunnelUrl = resultUrl
                isActive = true
                isStarting = false
                Log.i(TAG, "Pinggy Tunnel live: $resultUrl")
                onReady(resultUrl)
                return true
            }

            cleanupSsh()
            return false
        } catch (e: Exception) {
            Log.w(TAG, "Pinggy connection failed: ${e.message}")
            cleanupSsh()
            return false
        }
    }

    private fun tryConnectLocalhostRun(
        localPort: Int,
        onSettingUp: (String) -> Unit,
        onReady: (String) -> Unit
    ): Boolean {
        try {
            val jsch = JSch()
            ensureJSchKey(jsch)
            val session = jsch.getSession("nokey", "localhost.run", 22)
            session.setConfig("StrictHostKeyChecking", "no")
            session.setConfig("ServerAliveInterval", "30")
            session.timeout = 10000
            session.connect(10000)
            sshSession = session

            // Localhost.run listens on remote port 80
            session.setPortForwardingR(80, "127.0.0.1", localPort)
            Log.i(TAG, "Localhost.run port forwarding established")
            onSettingUp("Securing public link...")

            val channel = session.openChannel("exec") as ChannelExec
            channel.setCommand("")
            channel.connect(7000)
            sshChannel = channel

            val inStream = channel.inputStream
            val accumulator = StringBuilder()
            val lock = Object()
            var detectedUrl: String? = null

            val readerThread = thread(name = "Float-LHR-Reader") {
                val buf = ByteArray(1024)
                try {
                    while (channel.isConnected && detectedUrl == null && isStarting) {
                        val count = inStream.read(buf)
                        if (count == -1) break
                        val text = String(buf, 0, count)
                        synchronized(lock) {
                            accumulator.append(text)
                            val match = Regex("""https?://[a-zA-Z0-9-]+\.lhr\.(?:life|rocks)[^\s"'<>|]*""").find(accumulator)
                                ?: Regex("""https?://[a-zA-Z0-9-]+\.(?:a\.)?pinggy\.(?:link|io)[^\s"'<>|]*""").find(accumulator)
                            if (match != null) {
                                detectedUrl = match.value.trim().trimEnd('/', '|', '\\', ' ', '\t', '\r', '\n')
                            }
                        }
                        if (detectedUrl != null) break
                    }
                } catch (_: Exception) {}
            }

            val deadline = System.currentTimeMillis() + 10000 // 10 seconds max for Localhost.run
            while (System.currentTimeMillis() < deadline && detectedUrl == null && channel.isConnected && isStarting) {
                Thread.sleep(100)
            }

            val resultUrl = detectedUrl
            if (resultUrl != null) {
                tunnelUrl = resultUrl
                isActive = true
                isStarting = false
                Log.i(TAG, "Localhost.run Tunnel live: $resultUrl")
                onReady(resultUrl)
                return true
            }

            cleanupSsh()
            return false
        } catch (e: Exception) {
            Log.w(TAG, "Localhost.run connection failed: ${e.message}")
            cleanupSsh()
            return false
        }
    }

    /**
     * Headless Cloudflare Zero Trust Named Tunnel runner (ready in code for future token activation).
     * Does not expose UI/frontend design changes as requested.
     */
    fun startCloudflareZeroTrust(
        token: String,
        localPort: Int,
        onReady: (url: String) -> Unit,
        onError: (String) -> Unit
    ) {
        stopTunnel()
        isStarting = true

        thread(name = "Cloudflare-ZeroTrust") {
            try {
                val nativeDir = context.applicationInfo.nativeLibraryDir
                val binary = nativeDir?.let { File(it, "libcloudflared.so") }
                if (binary == null || !binary.exists()) {
                    isStarting = false
                    onError("Cloudflare binary not found")
                    return@thread
                }

                binary.setExecutable(true, false)
                val cmd = listOf(
                    binary.absolutePath,
                    "tunnel",
                    "--no-autoupdate",
                    "run",
                    "--token", token
                )

                val process = ProcessBuilder(cmd).apply {
                    directory(context.filesDir)
                    environment()["HOME"] = context.filesDir.parentFile?.absolutePath ?: context.filesDir.absolutePath
                    environment()["TMPDIR"] = context.cacheDir.absolutePath
                    redirectErrorStream(true)
                }.start()

                cfProcess = process
                val reader = process.inputStream.bufferedReader()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val raw = line.orEmpty()
                    Log.d(TAG, "cf-zerotrust: $raw")
                    if (raw.contains("Registered tunnel connection", ignoreCase = true)) {
                        isActive = true
                        isStarting = false
                        onReady("https://zero-trust-tunnel-active")
                    }
                }

                process.waitFor()
                isActive = false
                isStarting = false
            } catch (e: Exception) {
                isStarting = false
                isActive = false
                onError(e.message ?: "Zero Trust error")
            }
        }
    }

    private fun cleanupSsh() {
        try {
            sshChannel?.disconnect()
        } catch (ignored: Exception) {}
        sshChannel = null

        try {
            sshSession?.disconnect()
        } catch (ignored: Exception) {}
        sshSession = null
    }

    fun stopTunnel() {
        cleanupSsh()
        try {
            cfProcess?.destroy()
        } catch (ignored: Exception) {}
        cfProcess = null

        try {
            workerThread?.interrupt()
        } catch (ignored: Exception) {}
        workerThread = null

        isActive = false
        isStarting = false
        tunnelUrl = ""
        Log.i(TAG, "Online tunnel stopped")
    }
}
