package com.noishixzrn.floatapp.fragment

import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.noishixzrn.floatapp.MainActivity
import com.noishixzrn.floatapp.adapter.ExplorerAdapter
import com.noishixzrn.floatapp.databinding.FragmentExplorerBinding
import com.noishixzrn.floatapp.model.RemoteFileItem
import androidx.activity.result.contract.ActivityResultContracts
import com.noishixzrn.floatapp.utils.FileUtils
import com.noishixzrn.floatapp.utils.MediaPreviewDialog
import com.noishixzrn.floatapp.utils.StorageExplorerHelper
import java.io.File
import kotlin.concurrent.thread

class ExplorerFragment : Fragment() {

    private var _binding: FragmentExplorerBinding? = null
    private val binding get() = _binding!!
    private val gson = Gson()

    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            uploadFileToCurrentDir(uri)
        }
    }

    data class ExplorerVolume(
        val name: String,
        val path: String,
        val isRemote: Boolean = false,
        val remoteBaseUrl: String = ""
    )

    private var availableVolumes = mutableListOf<ExplorerVolume>()
    private var currentVolume: ExplorerVolume? = null
    private var currentPath: String = ""
    private val pathStack = mutableListOf<String>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentExplorerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val main = activity as? MainActivity ?: return

        val adapter = ExplorerAdapter { item ->
            if (item.isDirectory) {
                navigateTo(item.path)
            } else {
                val baseUrl = currentVolume?.remoteBaseUrl?.ifEmpty { main.getCurrentEndpoint() } ?: main.getCurrentEndpoint()
                MediaPreviewDialog.show(requireContext(), item, baseUrl)
            }
        }
        binding.rvExplorerItems.layoutManager = LinearLayoutManager(requireContext())
        binding.rvExplorerItems.adapter = adapter

        // Switch Drive button
        binding.btnSwitchRoot.setOnClickListener { showVolumePickerDialog() }

        // Back / Up button
        binding.btnNavigateUp.setOnClickListener {
            if (pathStack.isNotEmpty()) {
                val previous = pathStack.removeAt(pathStack.size - 1)
                loadDirectory(previous)
            } else if (currentVolume != null && currentPath != currentVolume?.path) {
                val parent = File(currentPath).parentFile?.path
                if (parent != null) {
                    loadDirectory(parent)
                }
            }
        }

        // Live Search Input
        binding.etSearchFiles.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val q = s?.toString()?.trim() ?: ""
                binding.btnClearSearch.visibility = if (q.isNotEmpty()) View.VISIBLE else View.GONE
                if (q.isNotEmpty()) {
                    performSearch(q)
                } else {
                    loadDirectory(currentPath)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnClearSearch.setOnClickListener {
            binding.etSearchFiles.text?.clear()
            loadDirectory(currentPath)
        }

        binding.fabUploadToDrive.setOnClickListener {
            showAddActionDialog()
        }

        initVolumes()
    }

    override fun onResume() {
        super.onResume()
        initVolumes()
    }

    private fun initVolumes() {
        val main = activity as? MainActivity ?: return
        thread {
            val list = mutableListOf<ExplorerVolume>()

            // 1. Local Android roots
            val localRoots = StorageExplorerHelper.getStorageRoots(requireContext())
            localRoots.forEach { r ->
                list.add(ExplorerVolume(r.name, r.path, isRemote = false))
            }

            // 2. Query connected PC / peer devices for remote drives
            var remoteVolumeToSelect: ExplorerVolume? = null
            main.knownPeerDevices.forEach { peer ->
                try {
                    val peerUrl = peer.endpointUrl
                    val conn = java.net.URL("$peerUrl/api/storage/roots").openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 3000
                    conn.readTimeout = 3000
                    if (conn.responseCode == 200) {
                        val json = conn.inputStream.bufferedReader().readText()
                        val type = object : TypeToken<List<Map<String, Any>>>() {}.type
                        val roots: List<Map<String, Any>> = gson.fromJson(json, type)
                        roots.forEach { rootMap ->
                            val rName = rootMap["name"] as? String ?: "Remote Drive"
                            val rPath = rootMap["path"] as? String ?: ""
                            if (rPath.isNotEmpty()) {
                                val vol = ExplorerVolume("${peer.name} · $rName", rPath, isRemote = true, remoteBaseUrl = peerUrl)
                                list.add(vol)
                                if (remoteVolumeToSelect == null) {
                                    remoteVolumeToSelect = vol
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            activity?.runOnUiThread {
                availableVolumes = list
                val targetVol = if (currentVolume == null || (!currentVolume!!.isRemote && remoteVolumeToSelect != null)) {
                    remoteVolumeToSelect ?: list.firstOrNull()
                } else {
                    currentVolume ?: list.firstOrNull()
                }
                if (targetVol != null) {
                    currentVolume = targetVol
                    binding.tvCurrentRootTitle.text = targetVol.name
                    pathStack.clear()
                    loadDirectory(targetVol.path)
                }
            }
        }
    }

    private fun showAddActionDialog() {
        val options = arrayOf("Upload File to this Folder", "Create New Folder")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Drive Storage Actions")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> pickFileLauncher.launch("*/*")
                    1 -> showCreateFolderDialog()
                }
            }
            .show()
    }

    private fun uploadFileToCurrentDir(uri: Uri) {
        val vol = currentVolume ?: return
        val destDir = currentPath.ifEmpty { vol.path }
        val fileName = FileUtils.getFileName(requireContext().contentResolver, uri)

        Toast.makeText(requireContext(), "Writing $fileName...", Toast.LENGTH_SHORT).show()

        thread {
            try {
                if (vol.isRemote) {
                    val encDir = Uri.encode(destDir)
                    val encName = Uri.encode(fileName)
                    val uploadUrl = "${vol.remoteBaseUrl}/api/storage/write?dir=$encDir&name=$encName"
                    val conn = java.net.URL(uploadUrl).openConnection() as java.net.HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/octet-stream")
                    val inputStream = requireContext().contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        inputStream.use { inp ->
                            conn.outputStream.use { out ->
                                inp.copyTo(out)
                            }
                        }
                    }
                    val code = conn.responseCode
                    activity?.runOnUiThread {
                        if (code == 200) {
                            Toast.makeText(requireContext(), "Written to PC: $fileName", Toast.LENGTH_LONG).show()
                            loadDirectory(destDir)
                        } else {
                            Toast.makeText(requireContext(), "Write failed ($code)", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    val destFile = File(destDir, fileName)
                    val inputStream = requireContext().contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        inputStream.use { inp ->
                            destFile.outputStream().use { out ->
                                inp.copyTo(out)
                            }
                        }
                    }
                    activity?.runOnUiThread {
                        Toast.makeText(requireContext(), "Saved: $fileName", Toast.LENGTH_SHORT).show()
                        loadDirectory(destDir)
                    }
                }
            } catch (e: Exception) {
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "Write error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showCreateFolderDialog() {
        val vol = currentVolume ?: return
        val destDir = currentPath.ifEmpty { vol.path }

        val input = android.widget.EditText(requireContext()).apply {
            hint = "Folder Name"
            setSingleLine(true)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Create New Folder")
            .setMessage("Location:\n$destDir")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    createFolder(destDir, name)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createFolder(destDir: String, name: String) {
        val vol = currentVolume ?: return
        thread {
            try {
                if (vol.isRemote) {
                    val url = "${vol.remoteBaseUrl}/api/storage/mkdir"
                    val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json")
                    val payload = gson.toJson(mapOf("dir" to destDir, "name" to name))
                    conn.outputStream.bufferedWriter().use { it.write(payload) }
                    val code = conn.responseCode
                    activity?.runOnUiThread {
                        if (code == 200) {
                            Toast.makeText(requireContext(), "Created folder: $name", Toast.LENGTH_SHORT).show()
                            loadDirectory(destDir)
                        } else {
                            Toast.makeText(requireContext(), "Could not create folder ($code)", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    val f = File(destDir, name)
                    val ok = f.mkdirs()
                    activity?.runOnUiThread {
                        if (ok || f.exists()) {
                            Toast.makeText(requireContext(), "Folder created", Toast.LENGTH_SHORT).show()
                            loadDirectory(destDir)
                        } else {
                            Toast.makeText(requireContext(), "Cannot create folder", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showVolumePickerDialog() {
        val names = availableVolumes.map { it.name }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Select Drive / Volume")
            .setItems(names) { _, which ->
                val vol = availableVolumes[which]
                currentVolume = vol
                binding.tvCurrentRootTitle.text = vol.name
                pathStack.clear()
                loadDirectory(vol.path)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun navigateTo(path: String) {
        if (currentPath.isNotEmpty()) {
            pathStack.add(currentPath)
        }
        loadDirectory(path)
    }

    private fun loadDirectory(path: String) {
        currentPath = path
        binding.tvBreadcrumbs.text = path
        val adapter = binding.rvExplorerItems.adapter as? ExplorerAdapter
        val vol = currentVolume ?: return

        thread {
            val items: List<RemoteFileItem> = if (vol.isRemote) {
                // Fetch from remote PC / peer device
                try {
                    val enc = Uri.encode(path)
                    val url = "${vol.remoteBaseUrl}/api/storage/list?path=$enc"
                    val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 4000
                    conn.readTimeout = 4000
                    if (conn.responseCode == 200) {
                        val json = conn.inputStream.bufferedReader().readText()
                        val type = object : TypeToken<List<RemoteFileItem>>() {}.type
                        gson.fromJson(json, type)
                    } else emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                // Local directory
                StorageExplorerHelper.listDirectory(path)
            }

            activity?.runOnUiThread {
                if (items.isEmpty()) {
                    binding.layoutEmptyExplorer.visibility = View.VISIBLE
                    binding.rvExplorerItems.visibility = View.GONE
                } else {
                    binding.layoutEmptyExplorer.visibility = View.GONE
                    binding.rvExplorerItems.visibility = View.VISIBLE
                    adapter?.submitList(items)
                }
            }
        }
    }

    private fun performSearch(query: String) {
        val adapter = binding.rvExplorerItems.adapter as? ExplorerAdapter
        val vol = currentVolume ?: return
        val baseSearchPath = currentPath.ifEmpty { vol.path }

        thread {
            val results: List<RemoteFileItem> = if (vol.isRemote) {
                try {
                    val encPath = Uri.encode(baseSearchPath)
                    val encQ = Uri.encode(query)
                    val url = "${vol.remoteBaseUrl}/api/storage/search?path=$encPath&q=$encQ"
                    val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 4000
                    conn.readTimeout = 4000
                    if (conn.responseCode == 200) {
                        val json = conn.inputStream.bufferedReader().readText()
                        val type = object : TypeToken<List<RemoteFileItem>>() {}.type
                        gson.fromJson(json, type)
                    } else emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                StorageExplorerHelper.searchFiles(baseSearchPath, query, maxResults = 80)
            }

            activity?.runOnUiThread {
                if (results.isEmpty()) {
                    binding.layoutEmptyExplorer.visibility = View.VISIBLE
                    binding.rvExplorerItems.visibility = View.GONE
                } else {
                    binding.layoutEmptyExplorer.visibility = View.GONE
                    binding.rvExplorerItems.visibility = View.VISIBLE
                    adapter?.submitList(results)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
