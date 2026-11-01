package com.noishixzrn.floatapp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.noishixzrn.floatapp.databinding.ActivityMainBinding
import com.noishixzrn.floatapp.databinding.DialogQrCodeBinding
import com.noishixzrn.floatapp.fragment.*
import com.noishixzrn.floatapp.model.DeviceItem
import com.noishixzrn.floatapp.server.FloatEmbeddedServer
import com.noishixzrn.floatapp.server.TunnelManager
import com.noishixzrn.floatapp.utils.DeviceTracer
import com.noishixzrn.floatapp.utils.NetworkUtils
import com.noishixzrn.floatapp.utils.QrUtils
import java.util.UUID
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var server: FloatEmbeddedServer? = null
    val tunnel by lazy { TunnelManager(this) }
    private var isOnlineMode = false
    private var serverPort = 8080
    private var isServerRunning = false
    var tunnelStatusMessage: String = ""

    val knownPeerDevices = mutableListOf<DeviceItem>()

    val deviceTrace by lazy { DeviceTracer.getHardwareTrace(this) }
    val deviceName by lazy { DeviceTracer.getDeviceLabel() }

    fun getServer() = server
    fun isServerRunning() = isServerRunning
    fun isOnlineMode() = isOnlineMode
    fun getServerPort() = serverPort

    fun getCurrentEndpoint(): String {
        return if (isOnlineMode && tunnel.tunnelUrl.isNotEmpty()) {
            tunnel.tunnelUrl
        } else {
            "http://${NetworkUtils.getLocalIpAddress(this)}:$serverPort"
        }
    }

    // Integrated ZXing Camera QR Scanner launcher
    private val qrScannerLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            handleScannedUrl(result.contents)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupTabs()

        // Read saved settings
        val prefs = getSharedPreferences("float_prefs", MODE_PRIVATE)
        serverPort = prefs.getInt("server_port", 8080)
        val autostart = prefs.getBoolean("autostart", true)

        if (autostart) startServer()

        // Mode pill toggle (Local <-> Online Tunnel)
        binding.pillModeToggle.setOnClickListener {
            toggleMode()
        }

        // Fan click: Host controller toggle (Start / Stop host / Offline)
        binding.layoutFanStatus.setOnClickListener {
            if (isServerRunning) stopServer() else startServer()
        }

        // Camera QR Scanner trigger
        binding.btnScanQr.setOnClickListener { launchQrScanner() }

        // Handle intent deep-links if opened via link
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        val url = if (data.scheme == "float") {
            data.getQueryParameter("url") ?: data.toString()
        } else {
            data.toString()
        }
        handleScannedUrl(url)
    }

    private fun setupTabs() {
        val fragments: List<Fragment> = listOf(
            DashboardFragment(),
            SendFragment(),
            ExplorerFragment(),
            MessagesFragment(),
            DevicesFragment(),
            SettingsFragment()
        )
        val tabLabels = listOf(
            getString(R.string.tab_dashboard),
            getString(R.string.tab_send),
            getString(R.string.tab_explorer),
            getString(R.string.tab_messages),
            getString(R.string.tab_devices),
            getString(R.string.tab_settings)
        )
        val tabIcons = listOf(
            R.drawable.ic_dashboard,
            R.drawable.ic_send,
            R.drawable.ic_folder,
            R.drawable.ic_chat,
            R.drawable.ic_devices,
            R.drawable.ic_settings
        )

        val adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = fragments.size
            override fun createFragment(pos: Int) = fragments[pos]
        }
        binding.viewPagerMain.adapter = adapter
        binding.viewPagerMain.isUserInputEnabled = false
        binding.viewPagerMain.offscreenPageLimit = 5

        TabLayoutMediator(binding.tabLayoutBottom, binding.viewPagerMain) { tab, pos ->
            tab.setIcon(tabIcons[pos])
        }.attach()
    }

    fun startServer() {
        if (isServerRunning) return
        val prefs = getSharedPreferences("float_prefs", MODE_PRIVATE)
        serverPort = prefs.getInt("server_port", 8080)
        val srvr = FloatEmbeddedServer(serverPort, this, deviceTrace, deviceName)
        server = srvr
        thread { srvr.start() }
        isServerRunning = true
        updateFanUi(running = true)
    }

    fun stopServer() {
        server?.stop()
        server = null
        isServerRunning = false
        tunnel.stopTunnel()
        isOnlineMode = false
        tunnelStatusMessage = ""
        updateFanUi(running = false)
        updateModePill()
        notifyModeChanged()
    }

    var onModeChangedListener: (() -> Unit)? = null

    fun notifyModeChanged() {
        runOnUiThread {
            onModeChangedListener?.invoke()
        }
    }

    fun toggleMode(
        onSettingUp: () -> Unit = {},
        onReady: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (!isServerRunning) {
            startServer()
        }
        if (isOnlineMode) {
            tunnel.stopTunnel()
            isOnlineMode = false
            tunnelStatusMessage = ""
            Toast.makeText(this, "Switched to Local Wi-Fi mode", Toast.LENGTH_SHORT).show()
            updateModePill()
            notifyModeChanged()
            onReady(getCurrentEndpoint())
        } else {
            isOnlineMode = true
            tunnelStatusMessage = "Server is setting up..."
            updateModePill()
            notifyModeChanged()
            tunnel.startTunnel(
                localPort = serverPort,
                onSettingUp = { status ->
                    runOnUiThread {
                        tunnelStatusMessage = status
                        notifyModeChanged()
                        onSettingUp()
                    }
                },
                onReady = { url ->
                    runOnUiThread {
                        tunnelStatusMessage = "Tunnel Active"
                        Toast.makeText(this, "Online Tunnel Active", Toast.LENGTH_SHORT).show()
                        notifyModeChanged()
                        onReady(url)
                    }
                },
                onError = { err ->
                    runOnUiThread {
                        tunnelStatusMessage = ""
                        isOnlineMode = false
                        updateModePill()
                        notifyModeChanged()
                        Toast.makeText(this, "Tunnel error: $err", Toast.LENGTH_LONG).show()
                        onError(err)
                    }
                }
            )
        }
    }

    private fun updateFanUi(running: Boolean) {
        runOnUiThread {
            if (running) {
                val rotate = AnimationUtils.loadAnimation(this, R.anim.rotate_fan)
                binding.ivFanStatus.startAnimation(rotate)
                binding.ivFanStatus.setColorFilter(ContextCompat.getColor(this, R.color.float_fan_green))
            } else {
                binding.ivFanStatus.clearAnimation()
                binding.ivFanStatus.setColorFilter(ContextCompat.getColor(this, R.color.float_fan_red))
            }
        }
    }

    private fun updateModePill() {
        runOnUiThread {
            if (isOnlineMode) {
                binding.tvModeText.text = getString(R.string.mode_online)
                binding.ivModeIcon.setImageResource(R.drawable.ic_cloud_tunnel)
            } else {
                binding.tvModeText.text = getString(R.string.mode_local)
                binding.ivModeIcon.setImageResource(R.drawable.ic_wifi)
            }
        }
    }

    /**
     * Opens a dialog of QR size displaying the high-res QR code for easy scanner detection.
     */
    fun showQrDialog(url: String) {
        val dialogBinding = DialogQrCodeBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.tvDialogQrUrl.text = url
        thread {
            val bmp = QrUtils.generateQrBitmap(url, 512)
            runOnUiThread { dialogBinding.ivDialogQrImage.setImageBitmap(bmp) }
        }

        dialogBinding.btnDialogQrClose.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnDialogQrCopy.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Float URL", url))
            Toast.makeText(this, "Copied!", Toast.LENGTH_SHORT).show()
        }
        dialogBinding.btnDialogQrShare.setOnClickListener {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, url)
            }
            startActivity(Intent.createChooser(intent, "Share Float link"))
        }

        dialog.show()
    }

    private fun launchQrScanner() {
        val options = ScanOptions().apply {
            setPrompt("")
            setBeepEnabled(true)
            setOrientationLocked(true)
            setCaptureActivity(CustomScannerActivity::class.java)
        }
        qrScannerLauncher.launch(options)
    }

    fun handleScannedUrl(scannedUrl: String) {
        val rawUrl = scannedUrl.trim()
        val normalized = if (!rawUrl.startsWith("http://") && !rawUrl.startsWith("https://")) {
            "http://$rawUrl"
        } else {
            rawUrl.trimEnd('/')
        }

        Toast.makeText(this, "Connecting to: $normalized", Toast.LENGTH_SHORT).show()

        // Probe peer for device health & hardware trace, and register phone with peer
        thread {
            try {
                val conn = java.net.URL("$normalized/api/health").openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                val resp = conn.inputStream.bufferedReader().readText()

                val trace = Regex(""""trace"\s*:\s*"([^"]+)"""").find(resp)?.groupValues?.get(1) ?: "HW-PC-PEER"
                val devName = Regex(""""device"\s*:\s*"([^"]+)"""").find(resp)?.groupValues?.get(1) ?: "Float PC"

                // Send registration to peer PC so PC's device section immediately shows this mobile phone
                try {
                    val postConn = java.net.URL("$normalized/api/devices/connect").openConnection() as java.net.HttpURLConnection
                    postConn.connectTimeout = 4000
                    postConn.readTimeout = 4000
                    postConn.requestMethod = "POST"
                    postConn.setRequestProperty("Content-Type", "application/json")
                    postConn.doOutput = true
                    val payload = """{"deviceId":"$deviceTrace","name":"$deviceName","kind":"mobile","trace":"$deviceTrace"}"""
                    postConn.outputStream.bufferedWriter().use { it.write(payload) }
                    val code = postConn.responseCode
                    Log.i("MainActivity", "Registered with PC: code $code")
                } catch (e: Exception) {
                    Log.w("MainActivity", "Registration post failed: ${e.message}")
                }

                // Ensure mobile embedded server is running
                if (!isServerRunning) {
                    runOnUiThread { startServer() }
                }

                val uri = Uri.parse(normalized)
                val port = if (uri.port > 0) uri.port else (if (normalized.startsWith("https")) 443 else 80)

                val peer = DeviceItem(UUID.randomUUID().toString(), devName, trace, normalized, port, true)
                runOnUiThread {
                    knownPeerDevices.removeAll { it.host == normalized || it.hardwareTrace == trace }
                    knownPeerDevices.add(0, peer)
                    Toast.makeText(this, "Connected: $devName ($trace)", Toast.LENGTH_LONG).show()

                    // Switch to Explorer tab to show PC disk drives immediately
                    binding.viewPagerMain.currentItem = 2
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Connected (Offline peer): $normalized", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopServer()
    }
}
