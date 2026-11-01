package com.noishixzrn.floatapp.fragment

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.noishixzrn.floatapp.MainActivity
import com.noishixzrn.floatapp.adapter.SharedFileAdapter
import com.noishixzrn.floatapp.databinding.FragmentDashboardBinding
import com.noishixzrn.floatapp.utils.QrUtils
import com.noishixzrn.floatapp.utils.StorageExplorerHelper
import java.io.File
import kotlin.concurrent.thread

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val refreshHandler = Handler(Looper.getMainLooper())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val main = activity as? MainActivity ?: return

        val adapter = SharedFileAdapter(
            onCopy = { item ->
                val url = if (item.isPrivate) "${main.getCurrentEndpoint()}/d/${item.id}?key=${item.secretKey}"
                          else "${main.getCurrentEndpoint()}/d/${item.id}"
                val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Float file link", url))
                Toast.makeText(requireContext(), if (item.isPrivate) "Private link copied!" else "Link copied!", Toast.LENGTH_SHORT).show()
            },
            onDelete = { item ->
                main.getServer()?.sharedFiles?.remove(item)
                refreshUi()
            }
        )
        binding.rvSharedFiles.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSharedFiles.adapter = adapter

        // Server switch
        binding.switchServerState.setOnCheckedChangeListener { _, checked ->
            if (checked) main.startServer() else main.stopServer()
            refreshUi()
        }

        // Small QR thumbnail tap -> opens dialog of QR size
        binding.ivSmallQrPreview.setOnClickListener {
            main.showQrDialog(getActiveEndpoint(main))
        }

        // Show QR button -> opens dialog of QR size
        binding.btnShowQr.setOnClickListener {
            main.showQrDialog(getActiveEndpoint(main))
        }

        // Copy endpoint
        binding.btnCopyEndpoint.setOnClickListener {
            val url = getActiveEndpoint(main)
            val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Float URL", url))
            Toast.makeText(requireContext(), "Copied!", Toast.LENGTH_SHORT).show()
        }

        // Share
        binding.btnShareHost.setOnClickListener {
            val url = getActiveEndpoint(main)
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, url)
            }
            startActivity(android.content.Intent.createChooser(intent, "Share Float link"))
        }

        // Folder Space setup
        val prefs = requireContext().getSharedPreferences("float_prefs", Context.MODE_PRIVATE)
        val defaultSpace = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.absolutePath
            ?: Environment.getExternalStorageDirectory().absolutePath
        val savedSpace = prefs.getString("folder_space_path", defaultSpace) ?: defaultSpace
        binding.tvSharedSpacePath.text = savedSpace

        binding.btnChangeFolderSpace.setOnClickListener {
            showFolderSpacePickerDialog()
        }

        // Share Restriction Switch (Allow Peer Storage Access)
        val allowStorage = prefs.getBoolean("allow_remote_storage", true)
        binding.switchShareRestriction.isChecked = allowStorage
        main.getServer()?.allowRemoteStorageAccess = allowStorage

        binding.switchShareRestriction.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("allow_remote_storage", isChecked).apply()
            main.getServer()?.allowRemoteStorageAccess = isChecked
            Toast.makeText(
                requireContext(),
                if (isChecked) "Peer access ALLOWED to folder space" else "Peer access RESTRICTED (Blocked)",
                Toast.LENGTH_SHORT
            ).show()
        }

        // Refresh button
        binding.btnRefreshDashboard.setOnClickListener { refreshUi() }

        // Mode change listener from MainActivity
        main.onModeChangedListener = {
            if (_binding != null) {
                refreshUi()
            }
        }

        // Auto-refresh every 3 seconds
        startAutoRefresh(adapter)
    }

    private fun getActiveEndpoint(main: MainActivity): String {
        return if (main.isOnlineMode() && main.tunnel.tunnelUrl.isNotEmpty()) {
            main.tunnel.tunnelUrl
        } else {
            main.getCurrentEndpoint()
        }
    }

    private fun showFolderSpacePickerDialog() {
        val roots = StorageExplorerHelper.getStorageRoots(requireContext())
        val names = roots.map { "${it.name} (${it.path})" }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Select Shared Folder Space")
            .setItems(names) { _, which ->
                val selected = roots[which].path
                binding.tvSharedSpacePath.text = selected
                requireContext().getSharedPreferences("float_prefs", Context.MODE_PRIVATE)
                    .edit().putString("folder_space_path", selected).apply()
                Toast.makeText(requireContext(), "Shared space set to ${roots[which].name}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startAutoRefresh(adapter: SharedFileAdapter) {
        val runnable = object : Runnable {
            override fun run() {
                if (_binding != null) {
                    refreshUi()
                    refreshHandler.postDelayed(this, 3000)
                }
            }
        }
        refreshHandler.postDelayed(runnable, 3000)
    }

    private fun refreshUi() {
        val main = activity as? MainActivity ?: return
        val isRunning = main.isServerRunning()
        val isOnline = main.isOnlineMode()
        val hasTunnel = main.tunnel.tunnelUrl.isNotEmpty()

        binding.switchServerState.isChecked = isRunning

        // Status title - crisp and modern
        binding.tvServerStatusTitle.text = when {
            !isRunning -> "Offline"
            isOnline && hasTunnel -> "Online Active"
            isOnline && main.tunnel.isStarting -> main.tunnelStatusMessage.ifEmpty { "Connecting..." }
            isOnline && !hasTunnel -> "Tunnel Offline"
            else -> "Local Host"
        }

        // Setting up progress indicator - only visible while actively starting
        if (isOnline && main.tunnel.isStarting) {
            binding.layoutTunnelSettingUp.visibility = View.VISIBLE
            binding.tvSettingUpText.text = main.tunnelStatusMessage.ifEmpty { "Connecting..." }
        } else {
            binding.layoutTunnelSettingUp.visibility = View.GONE
        }

        // URL display in one clean line
        val displayEndpoint = when {
            isOnline && hasTunnel -> main.tunnel.tunnelUrl
            isOnline && main.tunnel.isStarting -> "Generating link…"
            isOnline && !hasTunnel -> "Link unavailable"
            else -> main.getCurrentEndpoint()
        }
        binding.tvEndpointUrl.text = displayEndpoint

        // Small QR preview generation
        val qrUrl = if (isOnline && hasTunnel) main.tunnel.tunnelUrl else main.getCurrentEndpoint()
        thread {
            val smallBmp = QrUtils.generateQrBitmap(qrUrl, 160)
            activity?.runOnUiThread {
                if (_binding != null) {
                    binding.ivSmallQrPreview.setImageBitmap(smallBmp)
                }
            }
        }

        // Stats counts
        val server = main.getServer()
        val fileCount = server?.sharedFiles?.size ?: 0
        val peerCount = main.knownPeerDevices.size
        binding.tvDeviceCount.text = if (peerCount == 1) "1 Peer" else "$peerCount Peers"
        binding.tvSharedCount.text = if (fileCount == 1) "1 File" else "$fileCount Files"

        // File list
        val files = server?.sharedFiles?.toList() ?: emptyList()
        val adapter = binding.rvSharedFiles.adapter as? SharedFileAdapter
        if (files.isEmpty()) {
            binding.layoutEmptyShared.visibility = View.VISIBLE
            binding.rvSharedFiles.visibility = View.GONE
        } else {
            binding.layoutEmptyShared.visibility = View.GONE
            binding.rvSharedFiles.visibility = View.VISIBLE
            adapter?.submitList(files)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        (activity as? MainActivity)?.onModeChangedListener = null
        refreshHandler.removeCallbacksAndMessages(null)
        _binding = null
    }
}
