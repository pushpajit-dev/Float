'use strict';

const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const express = require('express');
const mime = require('mime-types');

let allowRemoteAccess = true;

function getWindowsDrives() {
  const drives = [];
  const letters = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ';
  for (let i = 0; i < letters.length; i++) {
    const driveLetter = letters[i] + ':\\';
    try {
      if (fs.existsSync(driveLetter)) {
        drives.push({ name: `Local Disk (${letters[i]}:)`, path: driveLetter, isRemovable: false });
      }
    } catch (_) {}
  }
  return drives;
}

function getSystemSpecialFolders() {
  const home = os.homedir();
  const folders = [
    { name: 'Desktop', path: path.join(home, 'Desktop') },
    { name: 'Downloads', path: path.join(home, 'Downloads') },
    { name: 'Documents', path: path.join(home, 'Documents') },
    { name: 'Pictures', path: path.join(home, 'Pictures') },
    { name: 'Videos', path: path.join(home, 'Videos') },
    { name: 'Music', path: path.join(home, 'Music') },
  ];
  return folders.filter((f) => {
    try {
      return fs.existsSync(f.path) && fs.statSync(f.path).isDirectory();
    } catch {
      return false;
    }
  });
}

function categorize(ext, isDir) {
  if (isDir) return 'folder';
  const clean = (ext || '').toLowerCase().replace('.', '');
  if (['jpg', 'jpeg', 'png', 'webp', 'gif', 'bmp', 'svg'].includes(clean)) return 'image';
  if (['mp4', 'mkv', 'webm', 'avi', 'mov', 'flv'].includes(clean)) return 'video';
  if (['mp3', 'wav', 'ogg', 'm4a', 'flac', 'aac'].includes(clean)) return 'audio';
  if (['html', 'htm'].includes(clean)) return 'html';
  if (['pdf', 'doc', 'docx', 'txt', 'md', 'csv', 'xlsx', 'pptx', 'json', 'xml'].includes(clean)) return 'doc';
  if (['exe', 'msi', 'apk', 'zip', 'rar', '7z', 'tar', 'gz'].includes(clean)) return 'app';
  return 'other';
}

function createStorageRouter() {
  const router = express.Router();

  // GET /api/storage/roots
  router.get('/api/storage/roots', (req, res) => {
    if (!allowRemoteAccess) {
      return res.status(403).json({ error: 'Remote drive access is disabled', roots: [] });
    }
    const roots = [...getSystemSpecialFolders(), ...getWindowsDrives()];
    res.json(roots);
  });

  // GET /api/storage/list?path=...
  router.get('/api/storage/list', (req, res) => {
    if (!allowRemoteAccess) {
      return res.status(403).json({ error: 'Remote drive access is disabled' });
    }
    const targetPath = req.query.path ? path.resolve(String(req.query.path)) : '';
    if (!targetPath || !fs.existsSync(targetPath)) {
      return res.status(404).json({ error: 'Path not found' });
    }

    try {
      const entries = fs.readdirSync(targetPath, { withFileTypes: true });
      const items = entries.map((e) => {
        const full = path.join(targetPath, e.name);
        let size = 0;
        let lastModified = 0;
        let isDir = false;
        try {
          const stat = fs.statSync(full);
          isDir = stat.isDirectory();
          size = isDir ? 0 : stat.size;
          lastModified = stat.mtimeMs;
        } catch {
          isDir = e.isDirectory();
        }
        const ext = path.extname(e.name);
        return {
          name: e.name,
          path: full,
          size,
          isDirectory: isDir,
          mimeType: isDir ? 'directory' : (mime.lookup(ext) || 'application/octet-stream'),
          category: categorize(ext, isDir),
          lastModified,
          downloadUrl: isDir ? null : `/api/storage/get?path=${encodeURIComponent(full)}`,
        };
      });

      items.sort((a, b) => {
        if (a.isDirectory !== b.isDirectory) return a.isDirectory ? -1 : 1;
        return a.name.localeCompare(b.name, undefined, { sensitivity: 'base' });
      });

      res.json(items);
    } catch (err) {
      res.status(500).json({ error: `Cannot read directory: ${err.message}` });
    }
  });

  // GET /api/storage/search?path=...&q=...
  router.get('/api/storage/search', (req, res) => {
    if (!allowRemoteAccess) {
      return res.status(403).json({ error: 'Remote drive access is disabled' });
    }
    const rootPath = req.query.path ? path.resolve(String(req.query.path)) : os.homedir();
    const query = String(req.query.q || '').trim().toLowerCase();
    if (!query) return res.json([]);

    const results = [];
    const maxResults = 100;

    function searchDir(dir, depth) {
      if (depth > 5 || results.length >= maxResults) return;
      try {
        const entries = fs.readdirSync(dir, { withFileTypes: true });
        for (const e of entries) {
          if (results.length >= maxResults) break;
          if (e.name.startsWith('$') || e.name.startsWith('.')) continue;

          const full = path.join(dir, e.name);
          if (e.name.toLowerCase().includes(query)) {
            let isDir = false;
            let size = 0;
            let mtime = 0;
            try {
              const stat = fs.statSync(full);
              isDir = stat.isDirectory();
              size = isDir ? 0 : stat.size;
              mtime = stat.mtimeMs;
            } catch {
              isDir = e.isDirectory();
            }
            const ext = path.extname(e.name);
            results.push({
              name: e.name,
              path: full,
              size,
              isDirectory: isDir,
              mimeType: isDir ? 'directory' : (mime.lookup(ext) || 'application/octet-stream'),
              category: categorize(ext, isDir),
              lastModified: mtime,
              downloadUrl: isDir ? null : `/api/storage/get?path=${encodeURIComponent(full)}`,
            });
          }

          if (e.isDirectory()) {
            searchDir(full, depth + 1);
          }
        }
      } catch (_) {}
    }

    searchDir(rootPath, 0);
    res.json(results);
  });

  // GET /api/storage/get?path=...
  router.get('/api/storage/get', (req, res) => {
    if (!allowRemoteAccess) {
      return res.status(403).send('Remote drive access is disabled');
    }
    const filePath = req.query.path ? path.resolve(String(req.query.path)) : '';
    if (!filePath || !fs.existsSync(filePath)) {
      return res.status(404).send('File not found');
    }

    let stat;
    try {
      stat = fs.statSync(filePath);
      if (!stat.isFile()) return res.status(400).send('Not a file');
    } catch {
      return res.status(500).send('File access error');
    }

    const total = stat.size;
    const name = path.basename(filePath);
    const mimeType = mime.lookup(name) || 'application/octet-stream';

    res.setHeader('Content-Type', mimeType);
    res.setHeader('Content-Disposition', `inline; filename="${encodeURIComponent(name)}"`);
    res.setHeader('Accept-Ranges', 'bytes');
    res.setHeader('Cache-Control', 'no-store');

    const range = req.headers.range;
    if (range) {
      const match = /^bytes=(\d*)-(\d*)$/.exec(range.trim());
      if (match) {
        let start = match[1] === '' ? total - parseInt(match[2], 10) : parseInt(match[1], 10);
        let end = match[2] === '' ? total - 1 : parseInt(match[2], 10);
        start = Math.max(0, start);
        end = Math.min(end, total - 1);
        if (!Number.isNaN(start) && !Number.isNaN(end) && start <= end) {
          res.status(206);
          res.setHeader('Content-Range', `bytes ${start}-${end}/${total}`);
          res.setHeader('Content-Length', end - start + 1);
          return fs.createReadStream(filePath, { start, end }).pipe(res);
        }
      }
    }

    res.status(200);
    res.setHeader('Content-Length', total);
    fs.createReadStream(filePath).pipe(res);
  });

  // POST /api/storage/toggle { allow }
  router.post('/api/storage/toggle', (req, res) => {
    if (typeof req.body.allow === 'boolean') {
      allowRemoteAccess = req.body.allow;
    }
    res.json({ allowRemoteAccess });
  });

  // POST /api/storage/write?dir=...&name=... (raw binary or octet-stream upload)
  router.post('/api/storage/write', (req, res) => {
    if (!allowRemoteAccess) {
      return res.status(403).json({ error: 'Remote drive access is disabled' });
    }
    const targetDir = req.query.dir ? path.resolve(String(req.query.dir)) : '';
    const fileName = req.query.name ? path.basename(String(req.query.name)) : '';
    if (!targetDir || !fileName || !fs.existsSync(targetDir)) {
      return res.status(400).json({ error: 'Invalid destination directory or filename' });
    }

    const destPath = path.join(targetDir, fileName);
    const writeStream = fs.createWriteStream(destPath);
    req.pipe(writeStream);

    writeStream.on('finish', () => {
      let size = 0;
      try { size = fs.statSync(destPath).size; } catch (_) {}
      res.json({ ok: true, path: destPath, name: fileName, size });
    });

    writeStream.on('error', (err) => {
      res.status(500).json({ error: `File write failed: ${err.message}` });
    });
  });

  // POST /api/storage/mkdir { dir, name }
  router.post('/api/storage/mkdir', (req, res) => {
    if (!allowRemoteAccess) {
      return res.status(403).json({ error: 'Remote drive access is disabled' });
    }
    const parentDir = req.body.dir ? path.resolve(String(req.body.dir)) : '';
    const folderName = req.body.name ? path.basename(String(req.body.name)) : '';
    if (!parentDir || !folderName || !fs.existsSync(parentDir)) {
      return res.status(400).json({ error: 'Invalid parent directory or folder name' });
    }

    const newPath = path.join(parentDir, folderName);
    try {
      fs.mkdirSync(newPath, { recursive: true });
      res.json({ ok: true, path: newPath });
    } catch (err) {
      res.status(500).json({ error: `Failed to create folder: ${err.message}` });
    }
  });

  // POST /api/storage/delete { path }
  router.post('/api/storage/delete', (req, res) => {
    if (!allowRemoteAccess) {
      return res.status(403).json({ error: 'Remote drive access is disabled' });
    }
    const targetPath = req.body.path ? path.resolve(String(req.body.path)) : '';
    if (!targetPath || !fs.existsSync(targetPath)) {
      return res.status(404).json({ error: 'Target path not found' });
    }

    try {
      fs.rmSync(targetPath, { recursive: true, force: true });
      res.json({ ok: true });
    } catch (err) {
      res.status(500).json({ error: `Delete failed: ${err.message}` });
    }
  });

  return { router };
}

module.exports = createStorageRouter;
