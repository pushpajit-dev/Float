package com.noishixzrn.floatapp.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.noishixzrn.floatapp.databinding.ItemMessageBinding
import com.noishixzrn.floatapp.model.NoteItem
import com.noishixzrn.floatapp.utils.FileUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessageAdapter(
    private val onCopy: (String) -> Unit,
    private val onAttachmentClick: (NoteItem) -> Unit = {}
) : ListAdapter<NoteItem, MessageAdapter.VH>(DIFF) {

    class VH(val binding: ItemMessageBinding) : RecyclerView.ViewHolder(binding.root)

    private val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        with(holder.binding) {
            tvMessageSender.text = item.sender
            tvMessageTime.text = timeFmt.format(Date(item.timestamp))
            tvMessageContent.text = item.text

            if (!item.attachmentName.isNullOrBlank()) {
                layoutMessageAttachment.visibility = View.VISIBLE
                tvAttachmentName.text = item.attachmentName
                tvAttachmentSize.text = FileUtils.formatFileSize(item.attachmentSize)
                layoutMessageAttachment.setOnClickListener { onAttachmentClick(item) }
            } else {
                layoutMessageAttachment.visibility = View.GONE
            }

            btnCopyMessage.setOnClickListener { onCopy(item.text) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<NoteItem>() {
            override fun areItemsTheSame(o: NoteItem, n: NoteItem) = o.id == n.id
            override fun areContentsTheSame(o: NoteItem, n: NoteItem) = o == n
        }
    }
}
