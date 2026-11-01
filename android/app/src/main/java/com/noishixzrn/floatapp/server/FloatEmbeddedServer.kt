package com.noishixzrn.floatapp.server

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import com.noishixzrn.floatapp.model.NoteItem
import com.noishixzrn.floatapp.model.TransferItem
import com.noishixzrn.floatapp.utils.FileUtils
import com.noishixzrn.floatapp.utils.StorageExplorerHelper
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.UUID
import kotlin.concurrent.thread

/**
 * Embedded HTTP server for Float Android matching PC Float core features.
 * Serves web dashboard, file downloads, storage/SD-card explorer, range streaming,
 * private link security, and message exchange with attachments.
 */
class FloatEmbeddedServer(
    private val port: Int,
    private val context: Context,
    private val deviceTrace: String,
    private val deviceName: String
) {
    private val TAG = "FloatServer"
    private val contentResolver: ContentResolver = context.contentResolver
    private var serverSocket: ServerSocket? = null
    @Volatile var isRunning = false
    var allowRemoteStorageAccess: Boolean = true

    val sharedFiles = mutableListOf<TransferItem>()
    val notes = mutableListOf<NoteItem>()
    val connectedDevices = mutableMapOf<String, Long>() // trace -> timestamp

    private val gson = Gson()

    fun start() {
        if (isRunning) return
        isRunning = true
        thread(name = "FloatServer-Main") {
            try {
                serverSocket = ServerSocket(port)
                Log.i(TAG, "Float server started on port $port")
                while (isRunning) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        thread(name = "FloatServer-Client") {
                            handleClient(client)
                        }
                    } catch (e: Exception) {
                        if (isRunning) Log.e(TAG, "Accept error: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server launch error: ${e.message}")
            }
        }
    }

    fun stop() {
        isRunning = false
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        Log.i(TAG, "Float server stopped")
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 15000
            val input = socket.getInputStream().bufferedReader()
            val output = socket.getOutputStream()

            val requestLine = input.readLine() ?: return
            val parts = requestLine.trim().split(" ")
            if (parts.size < 2) {
                respondError(output, 400, "Bad Request")
                return
            }
            val method = parts[0]
            val fullUrl = parts[1]
            val path = fullUrl.split("?").first()
            val queryString = if (fullUrl.contains("?")) fullUrl.substringAfter("?") else ""
            val queryParams = parseQueryParams(queryString)

            // Headers
            val headers = mutableMapOf<String, String>()
            var line = input.readLine()
            while (line != null && line.isNotBlank()) {
                val idx = line.indexOf(":")
                if (idx > 0) {
                    headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
                }
                line = input.readLine()
            }

            // Register peer trace if present
            headers["x-float-trace"]?.let { trace ->
                connectedDevices[trace] = System.currentTimeMillis()
            }

            when {
                // Preflight CORS
                method == "OPTIONS" -> respondOptions(output)

                // 1. Health check
                path == "/api/health" -> {
                    respondJson(output, gson.toJson(mapOf(
                        "ok" to true,
                        "app" to "Float",
                        "device" to deviceName,
                        "trace" to deviceTrace,
                        "platform" to "android",
                        "version" to "1.0.0"
                    )))
                }

                // 2. Public / Shared file list
                path == "/api/files" && method == "GET" -> {
                    val alive = sharedFiles.filter { !it.isExpired && !it.isPrivate }
                    respondJson(output, gson.toJson(alive.map { mapOf(
                        "id" to it.id,
                        "name" to it.name,
                        "size" to it.size,
                        "mimeType" to it.mimeType,
                        "category" to it.category,
                        "isOneTime" to it.isOneTime,
                        "isPrivate" to it.isPrivate,
                        "expiryTimeMs" to it.expiryTimeMs,
                        "downloadCount" to it.downloadCount,
                        "note" to it.note,
                        "downloadUrl" to "/d/${it.id}"
                    )}))
                }

                // 3. File download: /d/:token
                path.startsWith("/d/") -> {
                    val token = path.removePrefix("/d/").trimEnd('/')
                    val item = sharedFiles.firstOrNull { it.id == token }
                    if (item == null) {
                        respondHtml(output, 404, "File Not Found", "The requested file does not exist or has been removed.")
                        return
                    }
                    if (item.isExpired) {
                        sharedFiles.remove(item)
                        respondHtml(output, 410, "Link Expired", "This file transfer link has expired and is no longer available.")
                        return
                    }
                    if (item.isOneTime && item.downloadCount > 0) {
                        sharedFiles.remove(item)
                        respondHtml(output, 410, "One-Time Link Used", "This one-time download link has already been consumed.")
                        return
                    }

                    // Private check
                    if (item.isPrivate) {
                        val key = queryParams["key"] ?: headers["x-float-key"] ?: ""
                        if (key != item.secretKey && !key.equals(deviceTrace, ignoreCase = true)) {
                            respondHtml(output, 403, "Access Denied · Private File",
                                "This transfer is marked as Private. Only the authorized recipient or device with the correct access key can download it.")
                            return
                        }
                    }

                    streamTransferItem(output, item, headers["range"])
                }

                // 4. Notes / Messages GET
                path == "/api/notes" && method == "GET" -> {
                    respondJson(output, gson.toJson(notes.takeLast(60)))
                }

                // 5. Notes / Messages POST
                path == "/api/notes" && method == "POST" -> {
                    val contentLen = headers["content-length"]?.toIntOrNull() ?: 0
                    val body = if (contentLen > 0) {
                        val buf = CharArray(contentLen)
                        input.read(buf)
                        String(buf)
                    } else ""
                    try {
                        val map = gson.fromJson(body, Map::class.java)
                        val text = map["text"] as? String ?: ""
                        val sender = (map["sender"] as? String) ?: headers["x-float-trace"] ?: "Peer"
                        val attachName = map["attachmentName"] as? String
                        val attachSize = (map["attachmentSize"] as? Number)?.toLong() ?: 0L
                        val attachToken = map["attachmentToken"] as? String
                        if (text.isNotBlank() || !attachName.isNullOrBlank()) {
                            notes.add(NoteItem(
                                id = UUID.randomUUID().toString(),
                                sender = sender,
                                text = text,
                                attachmentName = attachName,
                                attachmentSize = attachSize,
                                attachmentToken = attachToken
                            ))
                        }
                    } catch (_: Exception) {}
                    respondJson(output, """{"ok":true}""")
                }

                // 6. Storage Explorer: Roots (Internal storage & SD Card)
                path == "/api/storage/roots" && method == "GET" -> {
                    if (!allowRemoteStorageAccess) {
                        respondJson(output, gson.toJson(mapOf("error" to "Storage access not permitted by host", "roots" to emptyList<Any>())))
                        return
                    }
                    val roots = StorageExplorerHelper.getStorageRoots(context)
                    respondJson(output, gson.toJson(roots))
                }

                // 7. Storage Explorer: List directory
                path == "/api/storage/list" && method == "GET" -> {
                    if (!allowRemoteStorageAccess) {
                        respondJson(output, gson.toJson(mapOf("error" to "Storage access disabled")))
                        return
                    }
                    val targetPath = queryParams["path"] ?: ""
                    val items = StorageExplorerHelper.listDirectory(targetPath)
                    respondJson(output, gson.toJson(items))
                }

                // 8. Storage Explorer: Search files
                path == "/api/storage/search" && method == "GET" -> {
                    if (!allowRemoteStorageAccess) {
                        respondJson(output, gson.toJson(emptyList<Any>()))
                        return
                    }
                    val searchPath = queryParams["path"] ?: StorageExplorerHelper.getStorageRoots(context).firstOrNull()?.path ?: ""
                    val query = queryParams["q"] ?: ""
                    val results = StorageExplorerHelper.searchFiles(searchPath, query)
                    respondJson(output, gson.toJson(results))
                }

                // 9. Storage Explorer: Stream/Get physical file
                path == "/api/storage/get" && method == "GET" -> {
                    if (!allowRemoteStorageAccess) {
                        respondHtml(output, 403, "Access Denied", "Remote storage access is disabled in Float settings.")
                        return
                    }
                    val targetPath = queryParams["path"] ?: ""
                    val file = File(targetPath)
                    if (!file.exists() || !file.isFile || !file.canRead()) {
                        respondHtml(output, 404, "File Not Found", "The requested storage file cannot be read.")
                        return
                    }
                    streamPhysicalFile(output, file, headers["range"])
                }

                // 9b. Storage Explorer: Write file
                path == "/api/storage/write" && method == "POST" -> {
                    if (!allowRemoteStorageAccess) {
                        respondJson(output, """{"error":"Storage access disabled"}""", 403)
                        return
                    }
                    val targetDir = queryParams["dir"] ?: ""
                    val fileName = queryParams["name"] ?: "upload_${System.currentTimeMillis()}"
                    val dir = File(targetDir)
                    if (!dir.exists() || !dir.isDirectory) {
                        respondJson(output, """{"error":"Invalid directory"}""", 400)
                        return
                    }
                    val outFile = File(dir, fileName)
                    val contentLen = headers["content-length"]?.toIntOrNull() ?: 0
                    val rawIn = socket.getInputStream()
                    outFile.outputStream().use { fos ->
                        if (contentLen > 0) {
                            var remaining = contentLen
                            val buf = ByteArray(8192)
                            while (remaining > 0) {
                                val r = rawIn.read(buf, 0, minOf(remaining, buf.size))
                                if (r == -1) break
                                fos.write(buf, 0, r)
                                remaining -= r
                            }
                        } else {
                            rawIn.copyTo(fos)
                        }
                    }
                    respondJson(output, """{"ok":true,"name":"$fileName","path":"${outFile.absolutePath}"}""")
                }

                // 9c. Storage Explorer: Mkdir
                path == "/api/storage/mkdir" && method == "POST" -> {
                    if (!allowRemoteStorageAccess) {
                        respondJson(output, """{"error":"Storage access disabled"}""", 403)
                        return
                    }
                    val contentLen = headers["content-length"]?.toIntOrNull() ?: 0
                    val body = if (contentLen > 0) {
                        val buf = CharArray(contentLen)
                        input.read(buf)
                        String(buf)
                    } else ""
                    val map = try { gson.fromJson(body, Map::class.java) } catch (_: Exception) { emptyMap<String, Any>() }
                    val dir = map["dir"] as? String ?: ""
                    val name = map["name"] as? String ?: ""
                    val target = File(dir, name)
                    val ok = target.mkdirs()
                    if (ok || target.exists()) {
                        respondJson(output, """{"ok":true,"path":"${target.absolutePath}"}""")
                    } else {
                        respondJson(output, """{"error":"Cannot create directory"}""", 500)
                    }
                }

                // 10. Web UI Dashboard at root /
                path == "/" || path == "/index.html" || path == "/explore" -> {
                    serveWebDashboard(output)
                }

                else -> respondError(output, 404, "Not Found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Request error: ${e.message}")
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun streamTransferItem(output: OutputStream, item: TransferItem, rangeHeader: String?) {
        val uri = Uri.parse(item.uriString ?: "")
        val inputStream = contentResolver.openInputStream(uri)
        if (inputStream == null) {
            respondError(output, 500, "Unable to read file stream")
            return
        }
        val bytes = inputStream.use { it.readBytes() }
        val total = bytes.size.toLong()

        // Handle one-time consumption
        if (item.isOneTime) {
            sharedFiles.remove(item)
        } else {
            item.downloadCount++
        }

        val pw = PrintWriter(output)
        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            val range = rangeHeader.removePrefix("bytes=").split("-")
            val start = range.getOrNull(0)?.toLongOrNull() ?: 0L
            val end = range.getOrNull(1)?.toLongOrNull() ?: (total - 1L)
            val clampedEnd = end.coerceAtMost(total - 1L)
            val length = clampedEnd - start + 1L

            pw.print("HTTP/1.1 206 Partial Content\r\n")
            pw.print("Content-Type: ${item.mimeType}\r\n")
            pw.print("Content-Disposition: attachment; filename=\"${item.name}\"\r\n")
            pw.print("Content-Range: bytes $start-$clampedEnd/$total\r\n")
            pw.print("Content-Length: $length\r\n")
            pw.print("Accept-Ranges: bytes\r\n")
            pw.print("Access-Control-Allow-Origin: *\r\n\r\n")
            pw.flush()

            output.write(bytes, start.toInt(), length.toInt())
            output.flush()
        } else {
            pw.print("HTTP/1.1 200 OK\r\n")
            pw.print("Content-Type: ${item.mimeType}\r\n")
            pw.print("Content-Disposition: attachment; filename=\"${item.name}\"\r\n")
            pw.print("Content-Length: $total\r\n")
            pw.print("Accept-Ranges: bytes\r\n")
            pw.print("Access-Control-Allow-Origin: *\r\n\r\n")
            pw.flush()

            output.write(bytes)
            output.flush()
        }
    }

    private fun streamPhysicalFile(output: OutputStream, file: File, rangeHeader: String?) {
        val total = file.length()
        val ext = file.extension.lowercase()
        val mime = when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "html", "htm" -> "text/html; charset=utf-8"
            "pdf" -> "application/pdf"
            "txt", "md" -> "text/plain; charset=utf-8"
            else -> "application/octet-stream"
        }

        val pw = PrintWriter(output)
        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            val range = rangeHeader.removePrefix("bytes=").split("-")
            val start = range.getOrNull(0)?.toLongOrNull() ?: 0L
            val end = range.getOrNull(1)?.toLongOrNull() ?: (total - 1L)
            val clampedEnd = end.coerceAtMost(total - 1L)
            val length = clampedEnd - start + 1L

            pw.print("HTTP/1.1 206 Partial Content\r\n")
            pw.print("Content-Type: $mime\r\n")
            pw.print("Content-Disposition: inline; filename=\"${file.name}\"\r\n")
            pw.print("Content-Range: bytes $start-$clampedEnd/$total\r\n")
            pw.print("Content-Length: $length\r\n")
            pw.print("Accept-Ranges: bytes\r\n")
            pw.print("Access-Control-Allow-Origin: *\r\n\r\n")
            pw.flush()

            FileInputStream(file).use { fis ->
                fis.skip(start)
                val buffer = ByteArray(16384)
                var remaining = length
                while (remaining > 0) {
                    val read = fis.read(buffer, 0, remaining.coerceAtMost(buffer.size.toLong()).toInt())
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    remaining -= read
                }
            }
            output.flush()
        } else {
            pw.print("HTTP/1.1 200 OK\r\n")
            pw.print("Content-Type: $mime\r\n")
            pw.print("Content-Disposition: inline; filename=\"${file.name}\"\r\n")
            pw.print("Content-Length: $total\r\n")
            pw.print("Accept-Ranges: bytes\r\n")
            pw.print("Access-Control-Allow-Origin: *\r\n\r\n")
            pw.flush()

            FileInputStream(file).use { fis ->
                fis.copyTo(output)
            }
            output.flush()
        }
    }

    private fun serveWebDashboard(output: OutputStream) {
        val html = """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Float · $deviceName</title>
  <style>
    :root { --bg:#0B0F17; --surface:#111827; --card:#1E293B; --emerald:#10B981; --cyan:#06B6D4; --text:#F8FAFC; --muted:#94A3B8; }
    * { box-sizing:border-box; margin:0; padding:0; font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif; }
    body { background:var(--bg); color:var(--text); padding:20px; line-height:1.5; }
    .header { display:flex; justify-content:space-between; align-items:center; padding-bottom:16px; border-bottom:1px solid #1E293B; margin-bottom:20px; }
    .logo { font-size:22px; font-weight:800; color:var(--emerald); display:flex; align-items:center; gap:8px; }
    .trace-badge { font-size:12px; font-family:monospace; background:rgba(16,185,129,0.15); color:var(--emerald); padding:4px 10px; border-radius:12px; border:1px solid rgba(16,185,129,0.3); }
    .tabs { display:flex; gap:10px; margin-bottom:20px; border-bottom:1px solid #1E293B; padding-bottom:10px; }
    .tab-btn { background:transparent; border:none; color:var(--muted); font-size:14px; font-weight:600; padding:8px 16px; border-radius:8px; cursor:pointer; }
    .tab-btn.active { background:var(--card); color:var(--emerald); }
    .card { background:var(--surface); border:1px solid #1E293B; border-radius:14px; padding:18px; margin-bottom:16px; }
    .file-item { display:flex; justify-content:space-between; align-items:center; padding:12px; background:var(--card); border-radius:10px; margin-bottom:8px; }
    .file-info { display:flex; flex-direction:column; }
    .file-name { font-weight:bold; font-size:14px; color:#F8FAFC; }
    .file-meta { font-size:12px; color:var(--muted); }
    .btn { background:var(--emerald); color:#022C22; font-weight:bold; text-decoration:none; padding:8px 14px; border-radius:8px; font-size:13px; border:none; cursor:pointer; display:inline-flex; align-items:center; }
    .search-input { width:100%; padding:10px 14px; border-radius:8px; background:var(--card); border:1px solid #334155; color:#fff; font-size:14px; margin-bottom:14px; }
    .breadcrumbs { font-size:13px; color:var(--muted); margin-bottom:12px; display:flex; gap:6px; flex-wrap:wrap; }
    .crumb-link { color:var(--cyan); cursor:pointer; text-decoration:underline; }
  </style>
</head>
<body>
  <div class="header">
    <div class="logo">⚡ Float · $deviceName</div>
    <div class="trace-badge">$deviceTrace</div>
  </div>

  <div class="tabs">
    <button class="tab-btn active" onclick="showTab('files')">Public Files</button>
    <button class="tab-btn" onclick="showTab('storage')">SD Card &amp; Storage Explorer</button>
  </div>

  <div id="tab-files">
    <div class="card">
      <h3 style="margin-bottom:12px;">Active Shared Files</h3>
      <div id="file-list">Loading files...</div>
    </div>
  </div>

  <div id="tab-storage" style="display:none;">
    <div class="card">
      <h3 style="margin-bottom:8px;">Android Storage &amp; Drives</h3>
      <p style="font-size:12px; color:var(--muted); margin-bottom:14px;">Browse Android internal storage, SD Card, photos, videos, audio, HTML &amp; documents directly from your PC browser.</p>
      <input type="text" id="storage-search" class="search-input" placeholder="Search files across storage &amp; SD card..." oninput="onSearchInput(this.value)">
      <div id="breadcrumbs" class="breadcrumbs"></div>
      <div id="storage-list">Loading storage roots...</div>
    </div>
  </div>

  <script>
    let currentPath = '';

    function showTab(t) {
      document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
      event.target.classList.add('active');
      document.getElementById('tab-files').style.display = t === 'files' ? 'block' : 'none';
      document.getElementById('tab-storage').style.display = t === 'storage' ? 'block' : 'none';
      if (t === 'storage' && !currentPath) loadStorageRoots();
    }

    async function loadFiles() {
      try {
        const res = await fetch('/api/files');
        const list = await res.json();
        const container = document.getElementById('file-list');
        if (!list || list.length === 0) {
          container.innerHTML = '<p style="color:#64748B;">No files currently shared.</p>';
          return;
        }
        container.innerHTML = list.map(f => `
          <div class="file-item">
            <div class="file-info">
              <span class="file-name">${"$"}{f.name}</span>
              <span class="file-meta">${"$"}{(f.size/1024/1024).toFixed(2)} MB · ${"$"}{f.isOneTime ? 'One-time download' : 'Public'}</span>
            </div>
            <a class="btn" href="${"$"}{f.downloadUrl}" download>Download</a>
          </div>
        `).join('');
      } catch(e) {
        document.getElementById('file-list').innerText = 'Failed to load files.';
      }
    }

    async function loadStorageRoots() {
      const res = await fetch('/api/storage/roots');
      const roots = await res.json();
      document.getElementById('breadcrumbs').innerHTML = '<b>Roots:</b>';
      const container = document.getElementById('storage-list');
      container.innerHTML = roots.map(r => `
        <div class="file-item" style="cursor:pointer;" onclick="openDirectory('${"$"}{encodeURIComponent(r.path)}')">
          <div class="file-info">
            <span class="file-name">📁 ${"$"}{r.name}</span>
            <span class="file-meta">${"$"}{r.path}</span>
          </div>
          <span style="color:var(--emerald); font-weight:bold;">Browse ›</span>
        </div>
      `).join('');
    }

    async function openDirectory(encPath) {
      currentPath = decodeURIComponent(encPath);
      document.getElementById('breadcrumbs').innerHTML = '<span class="crumb-link" onclick="loadStorageRoots()">Roots</span> / ' + currentPath;
      const res = await fetch('/api/storage/list?path=' + encodeURIComponent(currentPath));
      const items = await res.json();
      const container = document.getElementById('storage-list');
      if (items.length === 0) {
        container.innerHTML = '<p style="color:#64748B;">Empty directory.</p>';
        return;
      }
      container.innerHTML = items.map(it => {
        if (it.isDirectory) {
          return `
            <div class="file-item" style="cursor:pointer;" onclick="openDirectory('${"$"}{encodeURIComponent(it.path)}')">
              <div class="file-info">
                <span class="file-name">📁 ${"$"}{it.name}</span>
                <span class="file-meta">Folder</span>
              </div>
              <span style="color:var(--emerald);">Open ›</span>
            </div>
          `;
        } else {
          const downloadUrl = '/api/storage/get?path=' + encodeURIComponent(it.path);
          return `
            <div class="file-item">
              <div class="file-info">
                <span class="file-name">📄 ${"$"}{it.name}</span>
                <span class="file-meta">${"$"}{(it.size/1024).toFixed(1)} KB · ${"$"}{it.category}</span>
              </div>
              <a class="btn" href="${"$"}{downloadUrl}" download="${"$"}{it.name}">Download</a>
            </div>
          `;
        }
      }).join('');
    }

    let searchTimer = null;
    function onSearchInput(q) {
      clearTimeout(searchTimer);
      if (!q.trim()) {
        if (currentPath) openDirectory(encodeURIComponent(currentPath));
        else loadStorageRoots();
        return;
      }
      searchTimer = setTimeout(async () => {
        const res = await fetch('/api/storage/search?q=' + encodeURIComponent(q) + '&path=' + encodeURIComponent(currentPath || ''));
        const results = await res.json();
        const container = document.getElementById('storage-list');
        if (results.length === 0) {
          container.innerHTML = '<p style="color:#64748B;">No matching files found.</p>';
          return;
        }
        container.innerHTML = results.map(it => `
          <div class="file-item">
            <div class="file-info">
              <span class="file-name">${"$"}{it.isDirectory ? '📁' : '📄'} ${"$"}{it.name}</span>
              <span class="file-meta">${"$"}{it.path}</span>
            </div>
            ${"$"}{it.isDirectory ? '' : `<a class="btn" href="/api/storage/get?path=${"$"}{encodeURIComponent(it.path)}" download="${"$"}{it.name}">Download</a>`}
          </div>
        `).join('');
      }, 300);
    }

    loadFiles();
  </script>
</body>
</html>
        """.trimIndent()

        val bytes = html.toByteArray(Charsets.UTF_8)
        val pw = PrintWriter(output)
        pw.print("HTTP/1.1 200 OK\r\n")
        pw.print("Content-Type: text/html; charset=utf-8\r\n")
        pw.print("Content-Length: ${bytes.size}\r\n")
        pw.print("Access-Control-Allow-Origin: *\r\n\r\n")
        pw.flush()
        output.write(bytes)
        output.flush()
    }

    private fun respondJson(out: OutputStream, json: String, statusCode: Int = 200) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        val pw = PrintWriter(out)
        val statusText = if (statusCode == 200) "OK" else "Error"
        pw.print("HTTP/1.1 $statusCode $statusText\r\n")
        pw.print("Content-Type: application/json; charset=utf-8\r\n")
        pw.print("Content-Length: ${bytes.size}\r\n")
        pw.print("Access-Control-Allow-Origin: *\r\n\r\n")
        pw.flush()
        out.write(bytes)
        out.flush()
    }

    private fun respondHtml(out: OutputStream, statusCode: Int, title: String, message: String) {
        val html = """
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <title>$title · Float</title>
  <style>
    body { background:#0B0F17; color:#F8FAFC; font-family:-apple-system,BlinkMacSystemFont,sans-serif; display:flex; align-items:center; justify-content:center; height:100vh; margin:0; }
    .box { background:#111827; border:1px solid #1E293B; border-radius:16px; padding:32px; max-width:420px; text-align:center; box-shadow:0 10px 30px rgba(0,0,0,0.5); }
    h1 { color:#EF4444; font-size:20px; margin-bottom:12px; }
    p { color:#94A3B8; font-size:14px; line-height:1.6; }
    .badge { display:inline-block; margin-top:20px; font-size:12px; color:#10B981; font-family:monospace; }
  </style>
</head>
<body>
  <div class="box">
    <h1>$title</h1>
    <p>$message</p>
    <div class="badge">Float · $deviceTrace</div>
  </div>
</body>
</html>
        """.trimIndent()
        val bytes = html.toByteArray(Charsets.UTF_8)
        val pw = PrintWriter(out)
        val statusText = when (statusCode) {
            403 -> "Forbidden"
            404 -> "Not Found"
            410 -> "Gone"
            else -> "Error"
        }
        pw.print("HTTP/1.1 $statusCode $statusText\r\n")
        pw.print("Content-Type: text/html; charset=utf-8\r\n")
        pw.print("Content-Length: ${bytes.size}\r\n\r\n")
        pw.flush()
        out.write(bytes)
        out.flush()
    }

    private fun respondError(out: OutputStream, code: Int, msg: String) {
        val body = """{"error":"$msg"}"""
        val bytes = body.toByteArray()
        val pw = PrintWriter(out)
        pw.print("HTTP/1.1 $code Error\r\n")
        pw.print("Content-Type: application/json\r\n")
        pw.print("Content-Length: ${bytes.size}\r\n")
        pw.print("Access-Control-Allow-Origin: *\r\n\r\n")
        pw.flush()
        out.write(bytes)
        out.flush()
    }

    private fun respondOptions(out: OutputStream) {
        val pw = PrintWriter(out)
        pw.print("HTTP/1.1 204 No Content\r\n")
        pw.print("Access-Control-Allow-Origin: *\r\n")
        pw.print("Access-Control-Allow-Methods: GET,POST,OPTIONS\r\n")
        pw.print("Access-Control-Allow-Headers: Content-Type,X-Float-Trace,X-Float-Key,Range\r\n\r\n")
        pw.flush()
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (query.isBlank()) return map
        query.split("&").forEach { param ->
            val p = param.split("=")
            if (p.size == 2) {
                try {
                    map[URLDecoder.decode(p[0], "UTF-8")] = URLDecoder.decode(p[1], "UTF-8")
                } catch (_: Exception) {}
            }
        }
        return map
    }
}
