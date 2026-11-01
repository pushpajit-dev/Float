package com.noishixzrn.floatapp.fragment

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.noishixzrn.floatapp.MainActivity
import com.noishixzrn.floatapp.R
import com.noishixzrn.floatapp.adapter.StagedFileAdapter
import com.noishixzrn.floatapp.databinding.FragmentSendBinding
import com.noishixzrn.floatapp.model.TransferItem
import com.noishixzrn.floatapp.utils.FileUtils
import java.util.UUID

class SendFragment : Fragment() {

    private var _binding: FragmentSendBinding? = null
    private val binding get() = _binding!!

    private val stagedItems = mutableListOf<TransferItem>()
    private var selectedCategory = "all"

    private val filePicker = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        uris.forEach { addUri(it) }
        refreshStagedList()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSendBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = StagedFileAdapter { item ->
            stagedItems.remove(item)
            refreshStagedList()
        }
        binding.rvStagedFiles.layoutManager = LinearLayoutManager(requireContext())
        binding.rvStagedFiles.adapter = adapter

        // Category chips
        binding.chipGroupCategories.setOnCheckedStateChangeListener { group, _ ->
            selectedCategory = when (group.checkedChipId) {
                R.id.chipCatImages -> "images"
                R.id.chipCatVideos -> "videos"
                R.id.chipCatAudio -> "audio"
                R.id.chipCatDocs -> "docs"
                R.id.chipCatApks -> "apks"
                else -> "all"
            }
        }

        // Private vs Public toggle
        binding.switchPrivate.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.tvVisibilityLabel.text = getString(R.string.opt_private)
                binding.tvVisibilityDesc.text = "Only devices with secret key or direct link can access. Unauthorized get 403 Access Denied."
            } else {
                binding.tvVisibilityLabel.text = getString(R.string.opt_public)
                binding.tvVisibilityDesc.text = "Anyone with the link or on dashboard can access"
            }
        }

        // Dropzone tap
        binding.cardDropzone.setOnClickListener {
            val mimes = FileUtils.getMimeForCategory(selectedCategory)
            if (mimes.size == 1) filePicker.launch(mimes[0])
            else filePicker.launch("*/*")
        }

        // Send
        binding.btnSendFiles.setOnClickListener { doSend() }
    }

    private fun addUri(uri: Uri) {
        val cr = requireContext().contentResolver
        val originalName = FileUtils.getFileName(cr, uri)
        val size = FileUtils.getFileSize(cr, uri)
        val mime = FileUtils.getMimeType(cr, uri)

        // If user already typed custom name in rename box
        val customName = binding.etRenameFile.text.toString().trim()
        val finalName = if (customName.isNotEmpty()) customName else originalName

        val expiry = when (binding.chipGroupExpiry.checkedChipId) {
            R.id.chipExpiry5m -> System.currentTimeMillis() + 5 * 60 * 1000
            R.id.chipExpiry1h -> System.currentTimeMillis() + 60 * 60 * 1000
            R.id.chipExpiry1d -> System.currentTimeMillis() + 24 * 60 * 60 * 1000
            else -> 0L
        }

        val isPrivate = binding.switchPrivate.isChecked
        val secretKey = if (isPrivate) UUID.randomUUID().toString().replace("-", "").take(8) else ""

        stagedItems.add(TransferItem(
            id = UUID.randomUUID().toString().replace("-", "").take(16),
            name = finalName,
            size = size,
            uriString = uri.toString(),
            mimeType = mime,
            isPrivate = isPrivate,
            secretKey = secretKey,
            isOneTime = binding.switchOneTime.isChecked,
            expiryTimeMs = expiry,
            note = binding.etNote.text.toString().trim().takeIf { it.isNotEmpty() }
        ))
    }

    private fun refreshStagedList() {
        val adapter = binding.rvStagedFiles.adapter as? StagedFileAdapter
        if (stagedItems.isEmpty()) {
            binding.rvStagedFiles.visibility = View.GONE
        } else {
            binding.rvStagedFiles.visibility = View.VISIBLE
            adapter?.submitList(stagedItems.toList())
            if (stagedItems.size == 1 && binding.etRenameFile.text.isNullOrBlank()) {
                binding.etRenameFile.setText(stagedItems[0].name)
            }
        }
    }

    private fun doSend() {
        val main = activity as? MainActivity ?: return
        if (!main.isServerRunning()) {
            Toast.makeText(requireContext(), "Start the server first (tap the fan)", Toast.LENGTH_SHORT).show()
            return
        }
        if (stagedItems.isEmpty()) {
            Toast.makeText(requireContext(), "No files staged — tap the drop zone to pick files", Toast.LENGTH_SHORT).show()
            return
        }

        // Apply rename if user edited the name box
        val rename = binding.etRenameFile.text.toString().trim()
        if (rename.isNotEmpty() && stagedItems.size == 1) {
            stagedItems[0].name = rename
        }

        val server = main.getServer() ?: return
        server.sharedFiles.addAll(stagedItems)
        val count = stagedItems.size
        stagedItems.clear()
        refreshStagedList()
        binding.etRenameFile.text?.clear()
        binding.etNote.text?.clear()

        Toast.makeText(requireContext(), "$count file(s) shared successfully!", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
