package com.noishixzrn.floatapp

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.CaptureManager
import com.noishixzrn.floatapp.databinding.ActivityCustomScannerBinding

class CustomScannerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCustomScannerBinding
    private lateinit var capture: CaptureManager
    private var isTorchOn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Hide default ZXing status text and default viewfinder so custom square box shows cleanly
        try {
            binding.zxingBarcodeScanner.statusView.visibility = View.GONE
            binding.zxingBarcodeScanner.viewFinder.visibility = View.GONE
        } catch (ignored: Exception) {}

        capture = CaptureManager(this, binding.zxingBarcodeScanner)
        capture.initializeFromIntent(intent, savedInstanceState)
        capture.decode()

        binding.btnScannerClose.setOnClickListener {
            finish()
        }

        // Center bottom button: click to toggle torch / flash
        binding.btnScannerAction.setOnClickListener {
            isTorchOn = !isTorchOn
            if (isTorchOn) {
                binding.zxingBarcodeScanner.setTorchOn()
                binding.btnScannerAction.setColorFilter(ContextCompat.getColor(this, R.color.white))
            } else {
                binding.zxingBarcodeScanner.setTorchOff()
                binding.btnScannerAction.setColorFilter(ContextCompat.getColor(this, R.color.float_emerald))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        capture.onResume()
    }

    override fun onPause() {
        super.onPause()
        capture.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        capture.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        capture.onSaveInstanceState(outState)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        capture.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return binding.zxingBarcodeScanner.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)
    }
}
