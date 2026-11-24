#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
if ! command -v python3 >/dev/null 2>&1; then
  echo 'Python 3 not found. Install Python 3.11+ from https://www.python.org/downloads/macos/'
  exit 1
fi
python3 - <<'PY'
import sys
if sys.version_info < (3, 11):
    raise SystemExit(f'Python 3.11+ required; found {sys.version.split()[0]}')
print('Using Python', sys.version.split()[0])
PY
python3 -m venv .venv
.venv/bin/python -m pip install --upgrade pip
.venv/bin/python -m pip install -r requirements.txt
echo 'Setup complete. Next: ./run_tests_mac.sh'
