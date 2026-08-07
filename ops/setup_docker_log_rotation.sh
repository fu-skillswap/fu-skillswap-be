#!/bin/bash
# ST-07: Docker Log Rotation Setup
# Configures Docker daemon to limit JSON file logs to 10MB per file and 3 files max.
# This prevents Docker from consuming all VPS storage over time.

DAEMON_JSON="/etc/docker/daemon.json"

if [ "$EUID" -ne 0 ]; then
  echo "Please run as root to configure Docker daemon."
  exit 1
fi

if [ -f "$DAEMON_JSON" ]; then
    echo "Backing up existing $DAEMON_JSON to $DAEMON_JSON.bak"
    cp "$DAEMON_JSON" "$DAEMON_JSON.bak"
fi

echo "Writing log rotation config to $DAEMON_JSON..."

cat > "$DAEMON_JSON" << 'EOF'
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "3"
  }
}
EOF

echo "Config written successfully."
echo "Please restart docker to apply changes: systemctl restart docker"
