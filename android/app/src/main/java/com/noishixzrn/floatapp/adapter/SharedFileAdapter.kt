package com.noishixzrn.floatapp.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.noishixzrn.floatapp.databinding.ItemSharedFileBinding
import com.noishixzrn.floatapp.model.TransferItem
import com.noishixzrn.floatapp.utils.FileUtils

class SharedFileAdapter(
    private val onCopy: (TransferItem) -> Unit,
    private val onDelete: (TransferItem) -> Unit
) : ListAdapter<TransferItem, SharedFileAdapter.VH>(DIFF) {

    class VH(val binding: ItemSharedFileBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemSharedFileBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val context = holder.itemView.context
        with(holder.binding) {
            tvFileName.text = item.name
            tvFileSize.text = FileUtils.formatFileSize(item.size)
            if (item.isPrivate) {
                tvFileBadge.visibility = android.view.View.VISIBLE
                tvFileBadge.text = "Private"
                tvFileBadge.setTextColor(androidx.core.content.ContextCompat.getColor(context, com.noishixzrn.floatapp.R.color.float_fan_orange))
            } else if (item.isOneTime) {
                tvFileBadge.visibility = android.view.View.VISIBLE
                tvFileBadge.text = "One-Time"
                tvFileBadge.setTextColor(androidx.core.content.ContextCompat.getColor(context, com.noishixzrn.floatapp.R.color.float_cyan))
            } else {
                tvFileBadge.visibility = android.view.View.GONE
            }
            btnCopyFileLink.setOnClickListener { onCopy(item) }
            btnDeleteSharedFile.setOnClickListener { onDelete(item) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<TransferItem>() {
            override fun areItemsTheSame(o: TransferItem, n: TransferItem) = o.id == n.id
            override fun areContentsTheSame(o: TransferItem, n: TransferItem) = o == n
        }
    }
}
