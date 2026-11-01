package com.noishixzrn.floatapp.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.noishixzrn.floatapp.databinding.ItemDeviceBinding
import com.noishixzrn.floatapp.model.DeviceItem

class DeviceAdapter(
    private val onCall: (DeviceItem) -> Unit,
    private val onRemove: (DeviceItem) -> Unit,
    private val onBrowse: ((DeviceItem) -> Unit)? = null
) : ListAdapter<DeviceItem, DeviceAdapter.VH>(DIFF) {

    class VH(val binding: ItemDeviceBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        with(holder.binding) {
            tvDeviceName.text = item.name
            tvDeviceTrace.text = item.hardwareTrace
            tvDeviceIp.text = "${item.host}:${item.port}"
            btnBrowseDevice.setOnClickListener { onBrowse?.invoke(item) }
            root.setOnClickListener { onBrowse?.invoke(item) }
            btnCallDevice.setOnClickListener { onCall(item) }
            btnRemoveDevice.setOnClickListener { onRemove(item) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<DeviceItem>() {
            override fun areItemsTheSame(o: DeviceItem, n: DeviceItem) = o.id == n.id
            override fun areContentsTheSame(o: DeviceItem, n: DeviceItem) = o == n
        }
    }
}
