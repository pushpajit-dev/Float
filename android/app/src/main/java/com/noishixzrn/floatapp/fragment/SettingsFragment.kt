package com.noishixzrn.floatapp.fragment

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.gson.Gson
import com.noishixzrn.floatapp.MainActivity
import com.noishixzrn.floatapp.R
import com.noishixzrn.floatapp.databinding.FragmentSettingsBinding
import com.noishixzrn.floatapp.model.SessionData
import com.noishixzrn.floatapp.utils.DeviceTracer

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val gson = Gson()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val main = activity as? MainActivity ?: return
        val prefs = requireContext().getSharedPreferences("float_prefs", android.content.Context.MODE_PRIVATE)

        // Load saved values
        binding.etDeviceName.setText(prefs.getString("device_name", DeviceTracer.getDeviceLabel()))
        binding.etServerPort.setText(prefs.getInt("server_port", 8080).toString())
        binding.switchAutostart.isChecked = prefs.getBoolean("autostart", true)
        val storageAllowed = prefs.getBoolean("allow_remote_storage", true)
        binding.switchRemoteStorage.isChecked = storageAllowed
        main.getServer()?.allowRemoteStorageAccess = storageAllowed

        binding.switchRemoteStorage.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("allow_remote_storage", isChecked).apply()
            main.getServer()?.allowRemoteStorageAccess = isChecked
            Toast.makeText(requireContext(), if (isChecked) "Remote storage access enabled" else "Remote storage access disabled", Toast.LENGTH_SHORT).show()
        }

        // Save session
        binding.btnSaveSession.setOnClickListener {
            val session = SessionData(
                deviceName = binding.etDeviceName.text.toString(),
                serverPort = binding.etServerPort.text.toString().toIntOrNull() ?: 8080,
                autostart = binding.switchAutostart.isChecked,
                knownDevices = emptyList()
            )
            prefs.edit()
                .putString("device_name", session.deviceName)
                .putInt("server_port", session.serverPort)
                .putBoolean("autostart", session.autostart)
                .putString("session_json", gson.toJson(session))
                .apply()
            Toast.makeText(requireContext(), "Session saved", Toast.LENGTH_SHORT).show()
        }

        // Load session
        binding.btnLoadSession.setOnClickListener {
            val json = prefs.getString("session_json", null)
            if (json != null) {
                try {
                    val session = gson.fromJson(json, SessionData::class.java)
                    binding.etDeviceName.setText(session.deviceName)
                    binding.etServerPort.setText(session.serverPort.toString())
                    binding.switchAutostart.isChecked = session.autostart
                    Toast.makeText(requireContext(), "Session loaded", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Session data invalid", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "No saved session", Toast.LENGTH_SHORT).show()
            }
        }

        // Feedback — direct Gmail intent
        binding.btnFeedback.setOnClickListener {
            val email = getString(R.string.developer_email)
            val subject = getString(R.string.feedback_subject)
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email")).apply {
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, "")
            }
            try { startActivity(intent) } catch (e: Exception) {
                Toast.makeText(requireContext(), "No email app found", Toast.LENGTH_SHORT).show()
            }
        }

        // More Apps
        binding.btnMoreApps.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.more_apps_url))))
        }

        // GitHub
        binding.btnGithub.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.github_url))))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
