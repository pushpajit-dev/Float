// Online Tunnel manager for Float (PC / Node.js).
// Supports Pinggy / Localhost.run (zero-config, ISP-unblocked HTTPS reverse tunnels)
// and headless Cloudflare Zero Trust Named Tunnels (via token).

const { spawn, exec } = require('child_process');
const fs = require('fs');
const path = require('path');
const http = require('http');

let tunnelProcess = null;
let tunnelStatus = 'offline'; // 'offline' | 'starting' | 'online' | 'error'
let tunnelUrl = null;
let tunnelError = null;
let startedAt = null;

function findSsh() {
  const candidates = [
    'ssh',
    'C:\\Windows\\System32\\OpenSSH\\ssh.exe',
    'C:\\Program Files\\Git\\usr\\bin\\ssh.exe'
  ];
  for (const c of candidates) {
    try {
      if (fs.existsSync(c)) return c;
    } catch (_) {}
  }
  return 'ssh';
}

function findCloudflared() {
  const candidates = [
    path.join(path.dirname(process.execPath), 'cloudflared.exe'),
    path.join(__dirname, '..', 'cloudflared.exe'),
    path.join(__dirname, 'cloudflared.exe'),
    path.join(__dirname, '..', 'dist_app', 'cloudflared.exe'),
    'cloudflared.exe',
    'cloudflared'
  ];
  for (const c of candidates) {
    try {
      if (fs.existsSync(c)) return c;
    } catch (_) {}
  }
  return 'cloudflared.exe';
}

function getTunnelStatus() {
  return {
    active: tunnelStatus === 'online',
    status: tunnelStatus,
    url: tunnelUrl,
    error: tunnelError,
    startedAt
  };
}

function stopTunnel() {
  return new Promise((resolve) => {
    if (!tunnelProcess) {
      tunnelStatus = 'offline';
      tunnelUrl = null;
      tunnelError = null;
      startedAt = null;
      return resolve(getTunnelStatus());
    }

    const pid = tunnelProcess.pid;
    tunnelStatus = 'offline';
    tunnelUrl = null;
    tunnelError = null;
    startedAt = null;

    try {
      if (process.platform === 'win32' && pid) {
        exec(`taskkill /F /T /PID ${pid}`, () => {
          tunnelProcess = null;
          resolve(getTunnelStatus());
        });
        return;
      } else {
        tunnelProcess.kill('SIGTERM');
      }
    } catch (_) {}

    tunnelProcess = null;
    resolve(getTunnelStatus());
  });
}

/**
 * Starts online tunnel.
 * 1. Checks if a Cloudflare Zero Trust token is provided in environment (headless).
 * 2. Defaults to Pinggy / Localhost.run SSH tunnel (unblocked by ISPs, zero-config HTTPS).
 */
function startTunnel(port = 3000) {
  if (tunnelStatus === 'online' && tunnelUrl) {
    return Promise.resolve(getTunnelStatus());
  }

  if (tunnelStatus === 'starting') {
    return Promise.resolve(getTunnelStatus());
  }

  tunnelStatus = 'starting';
  tunnelError = null;
  tunnelUrl = null;
  startedAt = Date.now();

  const cfToken = process.env.CLOUDFLARE_TUNNEL_TOKEN;

  // Option A: Headless Cloudflare Zero Trust Named Tunnel
  if (cfToken && cfToken.trim().length > 10) {
    return startCloudflareNamedTunnel(cfToken.trim(), port);
  }

  // Option B: Pinggy / Localhost.run SSH reverse tunnel
  return startSshReverseTunnel(port);
}

function startCloudflareNamedTunnel(token, port) {
  const bin = findCloudflared();
  return new Promise((resolve) => {
    let resolved = false;
    const timeout = setTimeout(() => {
      if (!resolved) {
        resolved = true;
        if (!tunnelUrl) {
          tunnelStatus = 'error';
          tunnelError = 'Cloudflare Zero Trust tunnel timed out (30s).';
        }
        resolve(getTunnelStatus());
      }
    }, 30000);

    try {
      tunnelProcess = spawn(bin, ['tunnel', '--no-autoupdate', 'run', '--token', token], {
        windowsHide: true,
        stdio: ['ignore', 'pipe', 'pipe']
      });

      const handleOutput = (data) => {
        const text = data.toString();
        // Registered connection indicates live tunnel
        if (text.includes('Registered tunnel connection') && !tunnelUrl) {
          tunnelUrl = process.env.CLOUDFLARE_TUNNEL_URL || `https://cloudflare-tunnel-active.local`;
          tunnelStatus = 'online';
          if (!resolved) {
            resolved = true;
            clearTimeout(timeout);
            resolve(getTunnelStatus());
          }
        }
      };

      tunnelProcess.stdout.on('data', handleOutput);
      tunnelProcess.stderr.on('data', handleOutput);
      tunnelProcess.on('error', (err) => {
        tunnelStatus = 'error';
        tunnelError = `Failed to launch Cloudflare tunnel: ${err.message}`;
        if (!resolved) { resolved = true; clearTimeout(timeout); resolve(getTunnelStatus()); }
      });
      tunnelProcess.on('close', (code) => {
        if (tunnelStatus === 'online' || tunnelStatus === 'starting') {
          tunnelStatus = 'offline';
          if (code !== 0 && !tunnelError) tunnelError = `Tunnel exited with code ${code}`;
        }
        tunnelProcess = null;
        if (!resolved) { resolved = true; clearTimeout(timeout); resolve(getTunnelStatus()); }
      });
    } catch (err) {
      tunnelStatus = 'error';
      tunnelError = err.message;
      if (!resolved) { resolved = true; clearTimeout(timeout); resolve(getTunnelStatus()); }
    }
  });
}

function ensureSshKey() {
  const home = process.env.USERPROFILE || process.env.HOME || '';
  if (!home) return;
  const sshDir = path.join(home, '.ssh');
  const keyFile = path.join(sshDir, 'id_ed25519');
  const rsaFile = path.join(sshDir, 'id_rsa');
  if (fs.existsSync(keyFile) || fs.existsSync(rsaFile)) return;
  try {
    if (!fs.existsSync(sshDir)) fs.mkdirSync(sshDir, { recursive: true });
    exec(`ssh-keygen -t ed25519 -N "" -f "${keyFile}" -q`);
  } catch (_) {}
}

function startSshReverseTunnel(port) {
  const sshBin = findSsh();
  ensureSshKey();

  return new Promise((resolve) => {
    let resolved = false;
    let debuggerPoll = null;
    let fallbackTimer = null;

    const timeout = setTimeout(() => {
      if (!resolved) {
        resolved = true;
        if (debuggerPoll) clearInterval(debuggerPoll);
        if (fallbackTimer) clearTimeout(fallbackTimer);
        if (!tunnelUrl) {
          tunnelStatus = 'error';
          tunnelError = 'Online tunnel connection timed out. Please check your internet connection.';
        }
        resolve(getTunnelStatus());
      }
    }, 25000);

    const onDetectedUrl = (detected) => {
      if (!tunnelUrl && detected) {
        tunnelUrl = detected.trim();
        tunnelStatus = 'online';
        if (!resolved) {
          resolved = true;
          clearTimeout(timeout);
          if (fallbackTimer) clearTimeout(fallbackTimer);
          if (debuggerPoll) clearInterval(debuggerPoll);
          resolve(getTunnelStatus());
        }
      }
    };

    const tryLocalhostRunFallback = () => {
      if (tunnelUrl || resolved) return;
      try {
        if (tunnelProcess) {
          try { tunnelProcess.kill('SIGTERM'); } catch (_) {}
          tunnelProcess = null;
        }
      } catch (_) {}

      try {
        const lhrArgs = [
          '-o', 'StrictHostKeyChecking=no',
          '-o', 'ServerAliveInterval=30',
          `-R`, `80:localhost:${port}`,
          'nokey@localhost.run'
        ];
        tunnelProcess = spawn(sshBin, lhrArgs, {
          windowsHide: true,
          stdio: ['ignore', 'pipe', 'pipe']
        });

        const handleLhrOutput = (data) => {
          const text = data.toString();
          const lhrMatch = text.match(/https:\/\/[a-zA-Z0-9-]+\.lhr\.life[^\s"'<>|]*/);
          if (lhrMatch) {
            onDetectedUrl(lhrMatch[0]);
          }
        };

        tunnelProcess.stdout.on('data', handleLhrOutput);
        tunnelProcess.stderr.on('data', handleLhrOutput);
      } catch (_) {}
    };

    // Attempt Pinggy first over port 443
    const pinggyArgs = [
      '-p', '443',
      '-o', 'StrictHostKeyChecking=no',
      '-o', 'ServerAliveInterval=30',
      `-R0:localhost:${port}`,
      '-L4300:localhost:4300',
      'free.pinggy.io'
    ];

    try {
      tunnelProcess = spawn(sshBin, pinggyArgs, {
        windowsHide: true,
        stdio: ['ignore', 'pipe', 'pipe']
      });

      const handleOutput = (data) => {
        const text = data.toString();
        // Pinggy URL regex
        const pinggyMatch = text.match(/https?:\/\/[a-zA-Z0-9-]+\.a\.pinggy\.link[^\s"'<>|]*/);
        if (pinggyMatch) {
          onDetectedUrl(pinggyMatch[0]);
          return;
        }
        // Localhost.run regex
        const lhrMatch = text.match(/https:\/\/[a-zA-Z0-9-]+\.lhr\.life[^\s"'<>|]*/);
        if (lhrMatch) {
          onDetectedUrl(lhrMatch[0]);
          return;
        }
      };

      tunnelProcess.stdout.on('data', handleOutput);
      tunnelProcess.stderr.on('data', handleOutput);

      // Fast fallback to localhost.run if Pinggy doesn't yield URL within 7s
      fallbackTimer = setTimeout(() => {
        if (!tunnelUrl && !resolved) {
          tryLocalhostRunFallback();
        }
      }, 7000);

      // Periodically poll Pinggy's web debugger endpoint at http://localhost:4300/urls
      debuggerPoll = setInterval(() => {
        if (tunnelUrl) {
          clearInterval(debuggerPoll);
          return;
        }
        http.get('http://127.0.0.1:4300/urls', (res) => {
          let body = '';
          res.on('data', (c) => { body += c; });
          res.on('end', () => {
            try {
              const data = JSON.parse(body);
              if (Array.isArray(data.urls) && data.urls.length > 0) {
                const httpsUrl = data.urls.find((u) => u.startsWith('https://')) || data.urls[0];
                if (httpsUrl) onDetectedUrl(httpsUrl);
              }
            } catch (_) {}
          });
        }).on('error', () => {});
      }, 1000);

      tunnelProcess.on('error', () => {
        tryLocalhostRunFallback();
      });

      tunnelProcess.on('close', (code) => {
        if (!tunnelUrl && !resolved) {
          tryLocalhostRunFallback();
        } else if (tunnelStatus === 'online') {
          tunnelStatus = 'offline';
        }
        if (debuggerPoll) clearInterval(debuggerPoll);
      });
    } catch (err) {
      tunnelStatus = 'error';
      tunnelError = err.message;
      if (!resolved) {
        resolved = true;
        clearTimeout(timeout);
        if (debuggerPoll) clearInterval(debuggerPoll);
        resolve(getTunnelStatus());
      }
    }
  });
}

// Clean up on process exit
process.on('exit', () => {
  if (tunnelProcess && tunnelProcess.pid) {
    try {
      if (process.platform === 'win32') {
        exec(`taskkill /F /T /PID ${tunnelProcess.pid}`);
      } else {
        tunnelProcess.kill('SIGTERM');
      }
    } catch (_) {}
  }
});

module.exports = {
  getTunnelStatus,
  startTunnel,
  stopTunnel
};
