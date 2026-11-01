package com.noishixzrn.floatapp.fragment

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.noishixzrn.floatapp.MainActivity
import com.noishixzrn.floatapp.adapter.MessageAdapter
import com.noishixzrn.floatapp.databinding.BottomSheetEmojiBinding
import com.noishixzrn.floatapp.databinding.FragmentMessagesBinding
import com.noishixzrn.floatapp.model.NoteItem
import com.noishixzrn.floatapp.model.TransferItem
import com.noishixzrn.floatapp.utils.DeviceTracer
import com.noishixzrn.floatapp.utils.FileUtils
import java.util.UUID

class MessagesFragment : Fragment() {

    private var _binding: FragmentMessagesBinding? = null
    private val binding get() = _binding!!

    private val EMOJIS = listOf("😀","😂","😍","🙏","👍","🔥","✨","🎉","💯","❤️","😎","🤔","😅","👋","🌟","💬","📎","🔗","📁","⚡","🟢","✅")

    // Staged attachment for the next message
    private var stagedAttachmentUri: Uri? = null
    private var stagedAttachmentName: String? = null
    private var stagedAttachmentSize: Long = 0L

    private val attachmentPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val cr = requireContext().contentResolver
            stagedAttachmentUri = uri
            stagedAttachmentName = FileUtils.getFileName(cr, uri)
            stagedAttachmentSize = FileUtils.getFileSize(cr, uri)

            binding.layoutAttachedPreview.visibility = View.VISIBLE
            binding.tvStagedAttachmentName.text = stagedAttachmentName
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMessagesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val main = activity as? MainActivity ?: return

        val adapter = MessageAdapter(
            onCopy = { text ->
                val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("message", text))
                Toast.makeText(requireContext(), "Copied!", Toast.LENGTH_SHORT).show()
            },
            onAttachmentClick = { note ->
                // Download / Open attachment
                if (!note.attachmentToken.isNullOrBlank()) {
                    val url = "${main.getCurrentEndpoint()}/d/${note.attachmentToken}"
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } else if (!note.attachmentUri.isNullOrBlank()) {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(note.attachmentUri)).apply {
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        })
                    } catch (_: Exception) {
                        Toast.makeText(requireContext(), "Opening ${note.attachmentName}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
        binding.rvMessages.layoutManager = LinearLayoutManager(requireContext()).also { it.stackFromEnd = true }
        binding.rvMessages.adapter = adapter

        refreshMessages(adapter, main)

        // Attach file
        binding.btnAttachFile.setOnClickListener {
            attachmentPicker.launch("*/*")
        }

        // Clear attachment
        binding.btnClearAttachment.setOnClickListener {
            clearAttachment()
        }

        // Emoji picker
        binding.btnEmoji.setOnClickListener { showEmojiSheet() }

        // Send message
        binding.btnSendMessage.setOnClickListener {
            val text = binding.etMessageInput.text.toString().trim()
            if (text.isEmpty() && stagedAttachmentUri == null) return@setOnClickListener

            val server = main.getServer()
            val senderLabel = DeviceTracer.getDeviceLabel().substringBefore(" (")
            var attachToken: String? = null

            // If attachment is staged, create a TransferItem for it so peer devices can download it
            if (stagedAttachmentUri != null && server != null) {
                val token = UUID.randomUUID().toString().replace("-", "").take(16)
                attachToken = token
                val cr = requireContext().contentResolver
                val mime = FileUtils.getMimeType(cr, stagedAttachmentUri!!)
                server.sharedFiles.add(TransferItem(
                    id = token,
                    name = stagedAttachmentName ?: "attached_file",
                    size = stagedAttachmentSize,
                    uriString = stagedAttachmentUri.toString(),
                    mimeType = mime,
                    isPrivate = false,
                    isOneTime = false
                ))
            }

            val note = NoteItem(
                id = UUID.randomUUID().toString(),
                sender = senderLabel,
                text = text.ifEmpty { "Sent an attachment" },
                attachmentName = stagedAttachmentName,
                attachmentSize = stagedAttachmentSize,
                attachmentToken = attachToken,
                attachmentUri = stagedAttachmentUri?.toString()
            )

            server?.notes?.add(note)
            binding.etMessageInput.text?.clear()
            clearAttachment()
            refreshMessages(adapter, main)
        }
    }

    private fun clearAttachment() {
        stagedAttachmentUri = null
        stagedAttachmentName = null
        stagedAttachmentSize = 0L
        binding.layoutAttachedPreview.visibility = View.GONE
    }

    private fun refreshMessages(adapter: MessageAdapter, main: MainActivity) {
        val notes = main.getServer()?.notes?.takeLast(60) ?: emptyList()
        if (notes.isEmpty()) {
            binding.layoutEmptyMessages.visibility = View.VISIBLE
            binding.rvMessages.visibility = View.GONE
        } else {
            binding.layoutEmptyMessages.visibility = View.GONE
            binding.rvMessages.visibility = View.VISIBLE
            adapter.submitList(notes)
            binding.rvMessages.scrollToPosition(notes.size - 1)
        }
    }

    private fun showEmojiSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val sheetBinding = BottomSheetEmojiBinding.inflate(layoutInflater)

        EMOJIS.forEach { emoji ->
            val btn = android.widget.Button(requireContext()).apply {
                text = emoji
                textSize = 24f
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setOnClickListener {
                    binding.etMessageInput.append(emoji)
                    dialog.dismiss()
                }
            }
            sheetBinding.gridEmojis.addView(btn)
        }

        dialog.setContentView(sheetBinding.root)
        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        val main = activity as? MainActivity ?: return
        val adapter = binding.rvMessages.adapter as? MessageAdapter ?: return
        refreshMessages(adapter, main)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
