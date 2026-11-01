package com.noishixzrn.floatapp.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.noishixzrn.floatapp.databinding.ItemStagedFileBinding
import com.noishixzrn.floatapp.model.TransferItem
import com.noishixzrn.floatapp.utils.FileUtils

class StagedFileAdapter(
    private val onRemove: (TransferItem) -> Unit
) : ListAdapter<TransferItem, StagedFileAdapter.VH>(DIFF) {

    class VH(val binding: ItemStagedFileBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemStagedFileBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        with(holder.binding) {
            tvStagedFileName.text = item.name
            tvStagedFileSize.text = FileUtils.formatFileSize(item.size)
            btnRemoveStagedFile.setOnClickListener { onRemove(item) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<TransferItem>() {
            override fun areItemsTheSame(o: TransferItem, n: TransferItem) = o.id == n.id
            override fun areContentsTheSame(o: TransferItem, n: TransferItem) = o == n
        }
    }
}
