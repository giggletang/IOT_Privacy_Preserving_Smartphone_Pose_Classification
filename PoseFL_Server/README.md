# CSC 8223 PoseFL - Local Federated Learning Server

## Purpose
Windows laptop-hosted FastAPI + SQLite coordinator for two Android clients.
Compatible with the team's existing `FlClient.kt`:

- `GET /model` -> `{"round": 0, "weights": [214 numbers]}`
- `POST /update` <- `{"client_id":"A", "round":0, "n_samples":240, "weights":[214 numbers]}`

Additional endpoints: `GET /health`, `GET /status`, `GET /docs`.

The fixed model is 6-16-6 and 214 parameters:
- W1: 16x6, flattened row-major, offsets 0..95
- b1: offsets 96..111
- W2: 6x16, flattened row-major, offsets 112..207
- b2: offsets 208..213

Feature config for each database is **GRAV_NORM**. Do not mix feature configurations in one database. Both clients train their models **locally**; raw sensor data must never be sent to this server.

## Windows setup
1. Install Python 3.11 or newer from python.org (including the `py` launcher).
2. Unzip this folder somewhere such as `C:\\PoseFL_Server`.
3. Double-click `setup_windows.bat` once to install into `.venv`.
4. Double-click `run_tests.bat`. All tests should pass.
5. Double-click `start_local.bat` for a laptop-only test.
6. Visit `http://127.0.0.1:8000/health`, `/model`, `/status`, and `/docs` in a browser.

## Tailscale setup
1. Install Tailscale on your Windows laptop and your Android phone.
2. Invite the teammate to your tailnet or share only your Windows machine with the teammate.
3. The teammate accepts the invitation and installs Tailscale on Android.
4. Find your Windows laptop's Tailscale IPv4 address, such as `100.x.y.z`.
5. Stop `start_local.bat`; double-click `start_tailscale.bat` and enter **your actual Windows Tailscale IP**.
6. If Windows Firewall prompts for Python, allow only the required private/Tailscale connection. Prefer an inbound Windows Firewall rule scoped to the Tailscale interface / authorized peer IPs rather than a public all-networks rule.
7. Test from both Android browsers: `http://100.x.y.z:8000/health`. Do not use a sample/placeholder IP.
8. Enter `http://100.x.y.z:8000` in the Android app's Server URL field. Client A = `A`; Client B = `B`. Ensure Android network security configuration permits the chosen HTTP address, or use Tailscale Serve HTTPS instead.

**The laptop must remain powered on while FL is running.** No router port-forwarding, public IP, or cloud server is needed.

## Training procedure
1. Verify the six physical pose definitions, especially landscape left/right, before collecting the final labeled data.
2. Each phone collects its assigned non-IID training classes locally (`split=train`).
3. Both phone apps choose `GRAV_NORM`, point to the same server, and train through the FL Round button.
4. Phone A and B each call `GET /model` and independently train for three local epochs.
5. Phone A and B submit `POST /update` with the same round number.
6. The first update is stored and waits. The second update triggers sample-weighted FedAvg, atomically creating round `N+1` in SQLite.
7. Check `GET /status` for the current round and missing client. Proceed to the next round only after both updates have arrived.
8. Repeat until the experiment has completed its planned number of rounds.
9. Save local-only independent baselines separately in Android. Do not claim FL-trained models are the two independent baselines.

### Important
- The API cannot know whether the Android app selected `GRAV_NORM`; users must keep this setting fixed for the entire run.
- SQLite is stored at `data/posefl.sqlite3` and persists through restart. Make a backup when the server is stopped.
- The server binds to the **Tailscale IP only** in remote mode, limiting unwanted local-network exposure. Restrict network access to trusted Tailscale users. Authentication is not built into the inherited Android contract.
- A duplicate identical update is acknowledged with `already_received`. Different duplicate updates and stale/future rounds return HTTP 409. Do not change weights and re-submit for the same round.
- This is a two-client coursework prototype; not a hardened public internet service.
- To start an entirely new experiment, stop the server and **move/rename** `data/posefl.sqlite3` (and any `-wal`, `-shm` files) into a backed-up experiment folder. Then restart to initialize round 0. Never do this mid-round or while the server is running.
- **Do not** use `--reload` or several workers for an evaluated run.
