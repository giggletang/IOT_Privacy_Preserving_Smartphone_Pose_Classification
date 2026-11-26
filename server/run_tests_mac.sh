#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
if [ ! -x .venv/bin/python ]; then echo 'Run ./setup_mac.sh first'; exit 1; fi
.venv/bin/python -m pytest -q tests/
