// Express route for Cloudflare Tunnel management in Float.

const express = require('express');
const { getTunnelStatus, startTunnel, stopTunnel } = require('../tunnel');

function createTunnelRouter({ config }) {
  const router = express.Router();

  // GET current tunnel status
  router.get('/api/tunnel', (req, res) => {
    res.json({ ok: true, ...getTunnelStatus() });
  });

  // POST start tunnel
  router.post('/api/tunnel/start', async (req, res) => {
    try {
      const port = config.port || 3000;
      const status = await startTunnel(port);
      res.json({ ok: true, ...status });
    } catch (err) {
      res.status(500).json({ ok: false, error: err.message });
    }
  });

  // POST stop tunnel
  router.post('/api/tunnel/stop', async (req, res) => {
    try {
      const status = await stopTunnel();
      res.json({ ok: true, ...status });
    } catch (err) {
      res.status(500).json({ ok: false, error: err.message });
    }
  });

  return { router };
}

module.exports = { createTunnelRouter };
