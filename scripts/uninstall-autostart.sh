#!/usr/bin/env bash
# Remove MeghXL's auto-start (macOS / launchd).
# Usage:  npm run autostart:uninstall
set -euo pipefail

LABEL="com.noishixzen.float.server"
PLIST="$HOME/Library/LaunchAgents/$LABEL.plist"

launchctl unload "$PLIST" 2>/dev/null || true
rm -f "$PLIST"

echo "✅ Float auto-start removed. (Any running instance keeps going until you stop it.)"
