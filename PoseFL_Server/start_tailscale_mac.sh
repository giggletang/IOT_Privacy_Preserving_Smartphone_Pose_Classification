#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
if [ ! -x .venv/bin/python ]; then echo 'Run ./setup_mac.sh first'; exit 1; fi
printf 'Enter THIS MACBOOK Tailscale IPv4 (100.x.y.z): '
read -r SERVER_IP
if ! [[ "$SERVER_IP" =~ ^100\.([0-9]{1,3}\.){2}[0-9]{1,3}$ ]]; then
  echo 'Invalid-looking Tailscale IPv4. Verify it in Tailscale admin console.'
  exit 1
fi
echo "Starting http://${SERVER_IP}:8000"
exec .venv/bin/python -m uvicorn server:app --host "$SERVER_IP" --port 8000
