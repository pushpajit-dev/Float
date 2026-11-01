package com.noishixzrn.floatapp.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.noishixzrn.floatapp.R
import com.noishixzrn.floatapp.databinding.ItemExplorerFileBinding
import com.noishixzrn.floatapp.model.RemoteFileItem
import com.noishixzrn.floatapp.utils.FileUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExplorerAdapter(
    private val onItemClick: (RemoteFileItem) -> Unit
) : ListAdapter<RemoteFileItem, ExplorerAdapter.VH>(DIFF) {

    class VH(val binding: ItemExplorerFileBinding) : RecyclerView.ViewHolder(binding.root)

    private val dateFmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemExplorerFileBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val context = holder.itemView.context

        with(holder.binding) {
            tvExplorerItemName.text = item.name

            if (item.isDirectory) {
                ivExplorerItemIcon.setImageResource(R.drawable.ic_folder)
                ivExplorerItemIcon.setColorFilter(ContextCompat.getColor(context, R.color.float_emerald))
                tvExplorerItemMeta.text = if (item.lastModified > 0) dateFmt.format(Date(item.lastModified)) else "Directory"
                tvExplorerCategoryBadge.text = "DIR"
                tvExplorerCategoryBadge.setTextColor(ContextCompat.getColor(context, R.color.float_emerald_light))
                ivExplorerAction.setImageResource(R.drawable.ic_arrow_forward)
                ivExplorerAction.setColorFilter(ContextCompat.getColor(context, R.color.text_muted))
            } else {
                ivExplorerItemIcon.setImageResource(R.drawable.ic_file)
                val catBadge = item.category.uppercase(Locale.ROOT)
                tvExplorerCategoryBadge.text = catBadge

                val catColor = when (item.category) {
                    "image" -> R.color.float_emerald
                    "video" -> R.color.float_cyan
                    "audio" -> R.color.float_fan_orange
                    "html" -> R.color.float_emerald_light
                    else -> R.color.text_secondary
                }
                ivExplorerItemIcon.setColorFilter(ContextCompat.getColor(context, catColor))
                tvExplorerCategoryBadge.setTextColor(ContextCompat.getColor(context, catColor))

                val sizeStr = FileUtils.formatFileSize(item.size)
                val dateStr = if (item.lastModified > 0) " · " + dateFmt.format(Date(item.lastModified)) else ""
                tvExplorerItemMeta.text = "$sizeStr$dateStr"
                ivExplorerAction.setImageResource(R.drawable.ic_download)
                ivExplorerAction.setColorFilter(ContextCompat.getColor(context, R.color.float_emerald))
            }

            root.setOnClickListener { onItemClick(item) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<RemoteFileItem>() {
            override fun areItemsTheSame(o: RemoteFileItem, n: RemoteFileItem) = o.path == n.path
            override fun areContentsTheSame(o: RemoteFileItem, n: RemoteFileItem) = o == n
        }
    }
}
