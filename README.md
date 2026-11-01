# Float

<p align="center">
  <b>Transfer anything across local networks & the web — instantly, privately, at any size.</b><br>
  Self-hosted, cross-platform private file transfer hub for Windows, Android, and Web.<br>
  No accounts &middot; No third-party clouds &middot; No size limits &middot; Full PC Drive Explorer
</p>

<p align="center">
  <code>#file-transfer</code> &nbsp;<code>#self-hosted</code> &nbsp;<code>#local-network</code> &nbsp;<code>#android</code> &nbsp;<code>#windows</code> &nbsp;<code>#material3</code> &nbsp;<code>#privacy-first</code>
</p>

---

## What is Float?

**Float** is a modern, privacy-first, cross-platform file transfer and remote storage exploration system developed by **NoishiXzen**. It bridges your PC and mobile devices seamlessly with zero setup, whether on the same Wi-Fi network or across cellular data via secure online tunneling.

Drop a file into the desktop dashboard or your phone app, and it instantly syncs across all connected devices with live download links, interactive previews, and high-resolution QR codes.

---

## Key Features

- **Cross-Platform**: Native Android App, Windows Desktop App (Tauri / Bun executable), and responsive Web Dashboard.
- **Material 3 Design**: Warm Earth, Caramel & Espresso Brown palette (`#D4A373`, `#1E1915`, `#14110E`).
- **Bidirectional QR Pairing**: Scan a QR code on PC with the phone to instantly establish authenticated pairing and exchange Hardware Trace IDs.
- **Remote Drive Explorer**: Browse, read, stream, and **write** directly to PC local disks (`C:\`, `D:\`, Desktop, Downloads) from your phone, and browse Android storage from your PC.
- **Local & Online Modes**:
  - **Local Mode**: Blazing fast zero-cloud LAN transfers with mDNS service discovery.
  - **Online Tunnel Mode**: Pure-Java SSH reverse tunneling via Pinggy and Localhost.run over HTTPS port 443 — bypasses telecom/ISP NATs without port forwarding.
- **Private & Ephemeral Transfers**: Password/secret-key protected files, self-destructing links, and one-time downloads.
- **Built-in Media Streamer**: Live audio/video range streaming and inline image viewer.

---

## Project Structure

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

## Getting Started

### 1. Web & Server (Node.js / Bun)
```bash
# Clone the repository
git clone https://github.com/pushpajit-dev/float.git
cd float

# Install dependencies
npm install

# Run development server
npm start
# Server starts at http://localhost:3000
```

### 2. Android App
1. Open the `android/` directory in **Android Studio Ladybug / Koala**.
2. Sync Gradle files (JDK 17+ recommended).
3. Run or assemble the APK:
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

## Security & Privacy

- **Local Network Default**: Files never touch external servers during Local Mode.
- **No Cloud Accounts**: No third-party account registration, cookies, or telemetry tracking.
- **Memory-Safe Transfers**: Streaming file pipelines prevent RAM bloat even when transferring multi-gigabyte videos or ISOs.

---

## Author & Attribution

- **Developer**: NoishiXzen ([pushpajit-dev](https://github.com/pushpajit-dev))
- **Email**: noishixzen@gmail.com
- **Website**: [aivorygen.netlify.app](https://aivorygen.netlify.app)
- **License**: Apache License 2.0 (see [LICENSE](LICENSE) and [NOTICE](NOTICE)). Core transfer architecture based on MeghXL.
