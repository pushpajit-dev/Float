package com.noishixzrn.floatapp.utils

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebViewClient
import android.widget.MediaController
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.noishixzrn.floatapp.R
import com.noishixzrn.floatapp.databinding.DialogMediaPreviewBinding
import com.noishixzrn.floatapp.model.RemoteFileItem
import java.io.File
import kotlin.concurrent.thread

object MediaPreviewDialog {

    fun show(context: Context, item: RemoteFileItem, baseUrl: String) {
        val binding = DialogMediaPreviewBinding.inflate(LayoutInflater.from(context))
        val dialog = MaterialAlertDialogBuilder(context)
            .setView(binding.root)
            .create()

        binding.tvPreviewTitle.text = item.name
        binding.tvPreviewCategoryBadge.text = item.category.uppercase()
        binding.tvPreviewMeta.text = "${FileUtils.formatFileSize(item.size)} · ${item.category}"

        val streamUrl = if (item.downloadUrl != null && !item.downloadUrl.startsWith("http")) {
            "$baseUrl${item.downloadUrl}"
        } else {
            item.downloadUrl ?: "$baseUrl/api/storage/get?path=${Uri.encode(item.path)}"
        }

        var mediaPlayer: MediaPlayer? = null

        binding.btnPreviewClose.setOnClickListener {
            mediaPlayer?.release()
            dialog.dismiss()
        }

        binding.btnPreviewDownload.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(streamUrl))
            context.startActivity(intent)
            Toast.makeText(context, "Downloading ${item.name}", Toast.LENGTH_SHORT).show()
        }

        when (item.category.lowercase()) {
            "image" -> {
                binding.ivPreviewImage.visibility = View.VISIBLE
                binding.pbPreviewLoading.visibility = View.VISIBLE
                thread {
                    try {
                        val input = java.net.URL(streamUrl).openStream()
                        val bmp = BitmapFactory.decodeStream(input)
                        (context as? android.app.Activity)?.runOnUiThread {
                            binding.pbPreviewLoading.visibility = View.GONE
                            binding.ivPreviewImage.setImageBitmap(bmp)
                        }
                    } catch (_: Exception) {
                        // If local file
                        try {
                            val f = File(item.path)
                            if (f.exists()) {
                                val bmp = BitmapFactory.decodeFile(f.absolutePath)
                                (context as? android.app.Activity)?.runOnUiThread {
                                    binding.pbPreviewLoading.visibility = View.GONE
                                    binding.ivPreviewImage.setImageBitmap(bmp)
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
            }

            "video" -> {
                binding.vvPreviewVideo.visibility = View.VISIBLE
                binding.pbPreviewLoading.visibility = View.VISIBLE
                try {
                    val mediaController = MediaController(context)
                    mediaController.setAnchorView(binding.vvPreviewVideo)
                    binding.vvPreviewVideo.setMediaController(mediaController)
                    binding.vvPreviewVideo.setVideoURI(Uri.parse(streamUrl))
                    binding.vvPreviewVideo.setOnPreparedListener { mp ->
                        binding.pbPreviewLoading.visibility = View.GONE
                        mp.start()
                    }
                    binding.vvPreviewVideo.setOnErrorListener { _, _, _ ->
                        binding.pbPreviewLoading.visibility = View.GONE
                        Toast.makeText(context, "Cannot stream video inline. Opening externally...", Toast.LENGTH_SHORT).show()
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(streamUrl)))
                        true
                    }
                } catch (e: Exception) {
                    binding.pbPreviewLoading.visibility = View.GONE
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(streamUrl)))
                }
            }

            "audio" -> {
                binding.layoutAudioPlayer.visibility = View.VISIBLE
                binding.tvAudioTrackName.text = item.name
                binding.pbPreviewLoading.visibility = View.VISIBLE

                thread {
                    try {
                        val mp = MediaPlayer().apply {
                            setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .build()
                            )
                            setDataSource(streamUrl)
                            prepare()
                        }
                        mediaPlayer = mp
                        (context as? android.app.Activity)?.runOnUiThread {
                            binding.pbPreviewLoading.visibility = View.GONE
                            mp.start()
                            binding.btnAudioPlayPause.setImageResource(R.drawable.ic_close) // playing
                        }
                    } catch (e: Exception) {
                        (context as? android.app.Activity)?.runOnUiThread {
                            binding.pbPreviewLoading.visibility = View.GONE
                            Toast.makeText(context, "Streaming audio externally...", Toast.LENGTH_SHORT).show()
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(streamUrl)))
                        }
                    }
                }

                binding.btnAudioPlayPause.setOnClickListener {
                    val mp = mediaPlayer ?: return@setOnClickListener
                    if (mp.isPlaying) {
                        mp.pause()
                        binding.btnAudioPlayPause.setImageResource(R.drawable.ic_download)
                    } else {
                        mp.start()
                        binding.btnAudioPlayPause.setImageResource(R.drawable.ic_close)
                    }
                }
            }

            "html", "doc" -> {
                binding.wvPreviewDoc.visibility = View.VISIBLE
                binding.wvPreviewDoc.settings.javaScriptEnabled = true
                binding.wvPreviewDoc.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                        binding.pbPreviewLoading.visibility = View.GONE
                    }
                }
                binding.wvPreviewDoc.loadUrl(streamUrl)
            }

            else -> {
                // Other files: show preview card with direct download
                binding.layoutAudioPlayer.visibility = View.VISIBLE
                binding.tvAudioTrackName.text = item.name
                binding.btnAudioPlayPause.visibility = View.GONE
            }
        }

        dialog.setOnDismissListener {
            mediaPlayer?.release()
            mediaPlayer = null
            binding.vvPreviewVideo.stopPlayback()
        }

        dialog.show()
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}
