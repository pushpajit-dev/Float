<p align="center">
  <img src="https://i.imgur.com/JJOomaL.jpeg" alt="Gemini_Generated_Image_nnf2pdnnf2pdnnf2-clean" width="100%" style="max-width: 850px; border-radius: 14px; box-shadow: 0 8px 30px rgba(0,0,0,0.12);" />
</p>

# Float

<p align="center">
  <b>Transfer anything across local networks & the web — instantly, privately, at any size.</b><br>
  Self-hosted, cross-platform private file transfer hub for Windows, Android, and Web.<br>
  No accounts &middot; No third-party clouds &middot; No size limits &middot; Full PC Drive Explorer
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-8D6E63?style=for-the-badge" alt="Apache 2.0 license"></a>
  <img src="https://img.shields.io/badge/Platform-Windows%20%7C%20Android%20%7C%20Web-6D4C41?style=for-the-badge" alt="Platform"></a>
  <img src="https://img.shields.io/badge/Theme-Material%203%20Beige%20%26%20Brown-A1887F?style=for-the-badge" alt="Material 3"></a>
  <img src="https://img.shields.io/badge/Tunnel-Pinggy%20SSH-5D4037?style=for-the-badge" alt="Tunnel"></a>
</p>

<p align="center">
  <code>#file-transfer</code> &nbsp;<code>#self-hosted</code> &nbsp;<code>#local-network</code> &nbsp;<code>#android</code> &nbsp;<code>#windows</code> &nbsp;<code>#material3</code> &nbsp;<code>#privacy-first</code>
</p>

---

## 🚀 Downloads (Latest Windows Releases)

Get started immediately with the prebuilt binaries for Windows (64-bit):

| Release Type | Description | Download Link |
| :--- | :--- | :--- |
| **Windows Setup Installer** | Full desktop installer (`.exe`) with desktop & start menu shortcuts, auto-updater ready | [📥 **Download Float Installer (.exe)**](https://drive.google.com/file/d/1RL5JlWNJd7oFseWbyJc2B-_kidzDu7xD/view?usp=drive_link) |
| **Windows Portable** | Zero-installation standalone archive (`.zip`) — extract anywhere and run immediately | [📦 **Download Float Portable (.zip)**](https://drive.google.com/file/d/1Fl3Bbs4qfs2c5W4c4a1VbXKYUU7Afvvo/view?usp=drive_link) |

> [!TIP]
> **Quick Launch**: For the portable edition, simply extract the ZIP file to any folder and double-click `float.exe` No administrative rights required!

---

## What is Float?

**Float** is a modern, privacy-first, cross-platform file transfer and remote storage exploration system developed by **NoishiXzen**. It bridges your PC and mobile devices seamlessly with zero setup, whether on the same Wi-Fi network or across cellular data via secure online tunneling.

Drop a file into the desktop dashboard or your phone app, and it instantly syncs across all connected devices with live download links, interactive previews, and high-resolution QR codes.

---

## ✨ Key Features

- **📱 Cross-Platform Suite**:
  - **Native Android App**: Pure Kotlin with Jetpack Components, dynamic camera QR scanner, and background server service.
  - **Windows Desktop App**: High-performance Tauri + Rust desktop shell paired with compiled baseline binary.
  - **Modern Web Dashboard**: Responsive HTML5/ES6 application accessible from any browser on your network.
- **🎨 Material 3 Warm Earth / Brown Palette**:
  - Custom themed with Beige, Caramel, Sand, and Espresso Brown (`#D4A373`, `#1E1915`, `#14110E`).
- **🔗 Bidirectional QR Pairing**:
  - Scan the PC's pairing QR code with your phone (or vice-versa) to instantly link hardware IDs and authenticate without passwords.
- **📂 Remote PC Drive Explorer**:
  - Browse, read, stream, and **write** directly to PC local storage (`C:\`, `D:\`, Desktop, Documents, Downloads) directly from your mobile phone.
  - Browse phone storage directly from the PC dashboard.
- **🌐 Dual Connectivity (Local & Global)**:
  - **Local Mode**: Ultra-fast zero-cloud LAN transfers with mDNS automatic discovery.
  - **Online Tunnel Mode**: Pure-Java SSH reverse tunneling via Pinggy and Localhost.run over HTTPS port 443 — bypasses telecom/carrier NATs without port forwarding.
- **🔒 Private & Ephemeral Transfers**:
  - Password and secret-key protected files.
  - Self-destructing links and single-use downloads.
- **🎬 Built-in Media Streaming**:
  - Stream large audio and video files with HTTP byte-range seeking without waiting for full downloads.

---

## 📁 Repository Structure

```
float/
├── android/            # Native Android Studio project (Kotlin, Jetpack, JSch)
│   ├── app/src/main/   # UI Fragments, Embedded HTTP server, JSch SSH Tunnel
│   └── build.gradle.kts
├── desktop/            # Native Windows Desktop shell (Tauri + Rust)
│   ├── src-tauri/      # Rust backend & window manager
│   └── ui/             # Desktop frontend
├── installer/          # Inno Setup Windows installer configuration & assets
├── public/             # Web dashboard (HTML5, M3 CSS, vanilla JS, WebSockets)
├── scripts/            # Asset and autostart generation utilities
├── src/                # Node.js backend core
│   ├── routes/         # Storage, Tunnel, Files, Notes, Admin routes
│   ├── ws-hub.js       # WebSocket live broadcast & device roster
│   ├── tunnel.js       # OpenSSH / Pinggy reverse tunnel manager
│   └── config.js       # Port, host, and environment configuration
├── server.js           # Server entry point
└── package.json
```

---

## 🛠️ Building & Development

### 1. Web & Server (Node.js / Bun)
```bash
# Clone the repository
git clone https://github.com/pushpajit-dev/Float.git
cd Float

# Install dependencies
npm install

# Run development server
npm start
# Server starts at http://localhost:3000
```

### 2. Android App
1. Open the `android/` directory in **Android Studio Ladybug / Koala**.
2. Sync Gradle files (JDK 17+ recommended).
3. Build the APK:
   ```bash
   cd android
   ./gradlew assembleDebug
   ```

### 3. Windows Desktop Executable & Installer
- **Server binary**: Compile standalone server executable with Bun:
  ```bash
  bun build --compile --target=bun-windows-x64-baseline server.js --outfile dist_app/meghxl-server.exe
  ```
- **Windows Installer**: Compile with Inno Setup 6 using `installer/float_installer.iss`.

---

## 🛡️ Security & Privacy

- **Local Network Default**: Files never touch external cloud servers during Local Mode.
- **Zero Cloud Accounts**: No third-party account registration, no telemetry, no tracking cookies.
- **Memory-Safe Streaming**: Streaming file pipelines prevent RAM bloat even when transferring multi-gigabyte files.
- **Hardware Trace Pairing**: Pairing requests exchange cryptographic hardware IDs before granting remote file system permissions.

---

## 👤 Author & Attribution

- **Developer**: NoishiXzen ([pushpajit-dev](https://github.com/pushpajit-dev))
- **Email**: noishixzen@gmail.com
- **Website**: [aivorygen.netlify.app](https://aivorygen.netlify.app)
- **License**: Apache License 2.0 (see [LICENSE](LICENSE) and [NOTICE](NOTICE)). Core transfer architecture based on MeghXL.
