# macOS server setup

This document supplements [`README.md`](README.md). Run the following from the `server/` directory:

```bash
./setup_mac.sh
./run_tests_mac.sh
./start_tailscale_mac.sh
```

Enter the Mac's Tailscale IPv4 when prompted. Confirm the server responds at `http://SERVER_IP:8000/health` and `http://SERVER_IP:8000/status`, then verify both Android handsets can reach `/health` from within the same tailnet. Do not expose this unprotected HTTP service to the public Internet.

For server-only localhost testing use `./start_local_mac.sh`. The default SQLite DB is `server/data/posefl.sqlite3` and is created on first startup. For clean experiments use a separate DB location via `POSEFL_DB_PATH`; **back up the old DB first**.

For command-line setup on MacBook use `caffeinate -i` in a separate terminal during lengthy live demo sessions to prevent sleep. See [server README](README.md) and [root README](../README.md).
