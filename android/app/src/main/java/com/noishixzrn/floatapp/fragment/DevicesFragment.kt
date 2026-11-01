package com.noishixzrn.floatapp.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.noishixzrn.floatapp.MainActivity
import com.noishixzrn.floatapp.adapter.DeviceAdapter
import com.noishixzrn.floatapp.databinding.DialogAddDeviceBinding
import com.noishixzrn.floatapp.databinding.FragmentDevicesBinding
import com.noishixzrn.floatapp.model.DeviceItem
import com.noishixzrn.floatapp.utils.DeviceTracer
import java.util.UUID
import kotlin.concurrent.thread

class DevicesFragment : Fragment() {

    private var _binding: FragmentDevicesBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDevicesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvMyDeviceTrace.text = DeviceTracer.getHardwareTrace(requireContext())
        val main = activity as? MainActivity

        val adapter = DeviceAdapter(
            onCall = { device -> pingDevice(device) },
            onRemove = { device ->
                main?.knownPeerDevices?.remove(device)
                refreshList()
            },
            onBrowse = { _ ->
                main?.findViewById<androidx.viewpager2.widget.ViewPager2>(com.noishixzrn.floatapp.R.id.viewPagerMain)?.currentItem = 2
            }
        )
        binding.rvDevices.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDevices.adapter = adapter

        binding.btnAddDeviceManual.setOnClickListener { showAddDeviceDialog() }

        refreshList()
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun refreshList() {
        val main = activity as? MainActivity ?: return
        val adapter = binding.rvDevices.adapter as? DeviceAdapter
        val list = main.knownPeerDevices
        if (list.isEmpty()) {
            binding.layoutEmptyDevices.visibility = View.VISIBLE
            binding.rvDevices.visibility = View.GONE
        } else {
            binding.layoutEmptyDevices.visibility = View.GONE
            binding.rvDevices.visibility = View.VISIBLE
            adapter?.submitList(list.toList())
        }
    }

    private fun showAddDeviceDialog() {
        val dialogBinding = DialogAddDeviceBinding.inflate(layoutInflater)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add Peer Device")
            .setView(dialogBinding.root)
            .setPositiveButton("Add") { _, _ ->
                val host = dialogBinding.etDeviceHost.text.toString().trim()
                val port = dialogBinding.etDevicePort.text.toString().toIntOrNull() ?: 8080
                val nick = dialogBinding.etDeviceNickname.text.toString().trim().ifEmpty { host }
                if (host.isNotEmpty()) {
                    addDevice(host, port, nick)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addDevice(host: String, port: Int, name: String) {
        // Ping to get hardware trace
        thread {
            try {
                val url = if (host.startsWith("http")) host else "http://$host:$port"
                val conn = java.net.URL("$url/api/health").openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.requestMethod = "GET"
                val resp = conn.inputStream.bufferedReader().readText()
                val trace = Regex(""""trace"\s*:\s*"([^"]+)"""").find(resp)?.groupValues?.get(1) ?: "HW-TR-UNKN-UNKN"
                val serverName = Regex(""""device"\s*:\s*"([^"]+)"""").find(resp)?.groupValues?.get(1) ?: name

                val item = DeviceItem(UUID.randomUUID().toString(), serverName, trace, host, port, true)
                requireActivity().runOnUiThread {
                    val main = activity as? MainActivity
                    main?.knownPeerDevices?.removeAll { it.host == host }
                    main?.knownPeerDevices?.add(item)
                    refreshList()
                    Toast.makeText(requireContext(), "Connected to $serverName", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                requireActivity().runOnUiThread {
                    val main = activity as? MainActivity
                    val item = DeviceItem(UUID.randomUUID().toString(), name, "HW-TR-UNKN-UNKN", host, port, false)
                    main?.knownPeerDevices?.removeAll { it.host == host }
                    main?.knownPeerDevices?.add(item)
                    refreshList()
                    Toast.makeText(requireContext(), "Added $name (offline)", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun pingDevice(device: DeviceItem) {
        thread {
            try {
                val conn = java.net.URL("${device.endpointUrl}/api/health").openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 2000; conn.readTimeout = 2000
                val ok = conn.responseCode == 200
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), if (ok) "${device.name}: Online ✓" else "${device.name}: No response", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "${device.name}: Unreachable", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
