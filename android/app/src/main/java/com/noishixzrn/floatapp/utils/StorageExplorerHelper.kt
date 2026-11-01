package com.noishixzrn.floatapp.utils

import android.content.Context
import android.os.Environment
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import com.noishixzrn.floatapp.model.RemoteFileItem
import java.io.File
import java.util.Locale

object StorageExplorerHelper {

    data class StorageRoot(
        val name: String,
        val path: String,
        val isRemovable: Boolean = false
    )

    /**
     * Lists all accessible storage roots on this Android device (Internal, SD Card, etc.).
     */
    fun getStorageRoots(context: Context): List<StorageRoot> {
        val roots = mutableListOf<StorageRoot>()

        // 1. Primary Internal Storage
        val primary = Environment.getExternalStorageDirectory()
        if (primary != null && primary.exists()) {
            roots.add(StorageRoot("Internal Storage", primary.absolutePath, false))
        }

        // 2. Secondary volumes (Physical SD Card)
        val extDirs = ContextCompat.getExternalFilesDirs(context, null)
        for (dir in extDirs) {
            if (dir != null) {
                val path = dir.absolutePath
                if (!path.startsWith(primary?.absolutePath ?: "")) {
                    // Extract root up to Android/data
                    val idx = path.indexOf("/Android")
                    val sdPath = if (idx > 0) path.substring(0, idx) else path
                    val sdFile = File(sdPath)
                    if (sdFile.exists() && sdFile.canRead()) {
                        roots.add(StorageRoot("SD Card (${sdFile.name})", sdPath, true))
                    }
                }
            }
        }

        // 3. Fallback scan /storage directory directly
        try {
            val storageDir = File("/storage")
            if (storageDir.exists() && storageDir.isDirectory) {
                storageDir.listFiles()?.forEach { f ->
                    if (f.isDirectory && f.canRead() && f.name != "self" && f.name != "emulated") {
                        if (roots.none { it.path == f.absolutePath }) {
                            roots.add(StorageRoot("SD Card (${f.name})", f.absolutePath, true))
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // 4. Common standard directories as quick roots
        listOf(
            "Downloads" to Environment.DIRECTORY_DOWNLOADS,
            "DCIM / Photos" to Environment.DIRECTORY_DCIM,
            "Documents" to Environment.DIRECTORY_DOCUMENTS,
            "Music / Audio" to Environment.DIRECTORY_MUSIC,
            "Movies / Videos" to Environment.DIRECTORY_MOVIES
        ).forEach { (label, envType) ->
            val d = Environment.getExternalStoragePublicDirectory(envType)
            if (d != null && d.exists() && d.canRead()) {
                roots.add(StorageRoot(label, d.absolutePath, false))
            }
        }

        return roots.distinctBy { it.path }
    }

    /**
     * Lists files & folders inside a given directory path.
     */
    fun listDirectory(targetPath: String): List<RemoteFileItem> {
        val dir = File(targetPath)
        if (!dir.exists() || !dir.isDirectory || !dir.canRead()) return emptyList()

        val files = dir.listFiles() ?: return emptyList()
        val items = files.map { file ->
            val isDir = file.isDirectory
            val name = file.name
            val ext = file.extension.lowercase(Locale.ROOT)
            val mime = if (isDir) "directory" else getMimeFromExtension(ext)
            val category = when {
                isDir -> "folder"
                ext in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "svg") -> "image"
                ext in listOf("mp4", "mkv", "webm", "avi", "mov", "flv") -> "video"
                ext in listOf("mp3", "wav", "ogg", "m4a", "flac", "aac") -> "audio"
                ext in listOf("html", "htm") -> "html"
                ext in listOf("pdf", "doc", "docx", "txt", "md", "csv", "xlsx", "pptx") -> "doc"
                ext == "apk" -> "apk"
                else -> "other"
            }
            RemoteFileItem(
                name = name,
                path = file.absolutePath,
                size = if (isDir) 0L else file.length(),
                isDirectory = isDir,
                mimeType = mime,
                category = category,
                lastModified = file.lastModified()
            )
        }

        // Directories first, then alphabetical
        return items.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase(Locale.ROOT) }))
    }

    /**
     * Recursively searches for files matching a query starting from searchPath (up to maxResults).
     */
    fun searchFiles(searchPath: String, query: String, maxResults: Int = 100): List<RemoteFileItem> {
        val results = mutableListOf<RemoteFileItem>()
        val startDir = File(searchPath)
        if (!startDir.exists() || !startDir.canRead() || query.isBlank()) return results

        val cleanQuery = query.lowercase(Locale.ROOT).trim()

        fun searchRecursive(dir: File, depth: Int) {
            if (depth > 6 || results.size >= maxResults) return
            val files = dir.listFiles() ?: return
            for (f in files) {
                if (results.size >= maxResults) break
                if (f.name.startsWith(".")) continue
                if (f.name.lowercase(Locale.ROOT).contains(cleanQuery)) {
                    val isDir = f.isDirectory
                    val ext = f.extension.lowercase(Locale.ROOT)
                    val mime = if (isDir) "directory" else getMimeFromExtension(ext)
                    val cat = when {
                        isDir -> "folder"
                        ext in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "svg") -> "image"
                        ext in listOf("mp4", "mkv", "webm", "avi", "mov") -> "video"
                        ext in listOf("mp3", "wav", "ogg", "m4a", "flac") -> "audio"
                        ext in listOf("html", "htm") -> "html"
                        ext in listOf("pdf", "doc", "docx", "txt", "md") -> "doc"
                        else -> "other"
                    }
                    results.add(RemoteFileItem(
                        name = f.name,
                        path = f.absolutePath,
                        size = if (isDir) 0L else f.length(),
                        isDirectory = isDir,
                        mimeType = mime,
                        category = cat,
                        lastModified = f.lastModified()
                    ))
                }
                if (f.isDirectory && f.canRead()) {
                    searchRecursive(f, depth + 1)
                }
            }
        }

        searchRecursive(startDir, 0)
        return results
    }

    private fun getMimeFromExtension(ext: String): String {
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: when (ext) {
            "html", "htm" -> "text/html"
            "md", "txt" -> "text/plain"
            "apk" -> "application/vnd.android.package-archive"
            "pdf" -> "application/pdf"
            else -> "application/octet-stream"
        }
    }
}
