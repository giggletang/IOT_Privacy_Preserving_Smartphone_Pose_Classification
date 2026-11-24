# PoseFL Server — macOS installation

This package uses the exact Android client API contract: `GET /model` and `POST /update`. Only model parameters and training metadata reach the server. `GRAV_NORM` is the configured feature type. The database is `data/posefl.sqlite3` and persists between restarts.

## Install and test

1. Install Python 3.11 or later (python.org macOS installer), and confirm `python3 --version` in Terminal.
2. Unzip `PoseFL_Server_macOS.zip` into `~/Documents/` (folder `PoseFL_Server`).
3. In Terminal: `cd ~/Documents/PoseFL_Server`
4. If needed: `chmod +x *_mac.sh`
5. Run `./setup_mac.sh` to create a virtual environment and install Python dependencies.
6. Run `./run_tests_mac.sh`; expected result is 4 passed.
7. Run `./start_local_mac.sh`; test `http://127.0.0.1:8000/health`, `/model`, and `/docs` in the Mac browser. Stop with Ctrl+C.

## Tailscale private network

1. Install Tailscale on the Mac from https://tailscale.com/download/mac and sign in.
2. Install Tailscale on both Android phones. Sign in to the same authorized tailnet (invite your teammate or share just your MacBook machine).
3. In https://login.tailscale.com/admin/machines find the MacBook's **100.x.y.z** Tailscale IPv4 (not the Android phone's IP).
4. In Terminal run `./start_tailscale_mac.sh` and enter that MacBook IP. Keep the Terminal window open, and keep the Mac awake during federated training.
5. On both Android phones, turn Tailscale on and open `http://MAC_TAILSCALE_IP:8000/health` in their browsers.
6. Set the Android app Server URL to `http://MAC_TAILSCALE_IP:8000`, Client ID `A` on your teammate's phone and `B` on yours, and feature configuration `GRAV_NORM`. The Android app may require replacing the placeholder `100.x.y.z` in its `network_security_config.xml` with the actual MacBook IP, then rebuilding. See `android_integration/README.txt`.

**Do not open port 8000 on your router.** This local course service has no HTTP authentication; restrict tailnet access to your team members, and never expose it to the public internet. macOS firewall may prompt for permission to accept incoming connections for Python: allow approved Tailscale traffic. If requests from phones fail, verify Tailscale access rules and the local macOS firewall.

## Notes

- Use one server process (do not run `start_local_mac.sh` and `start_tailscale_mac.sh` simultaneously on the same port).
- Use a unique database file per `GRAV_NORM` vs. `BASELINE`/`ROBUST` experimental run; do not mix feature configurations.
- The final report requires two independent local-only baselines plus the global model. The server does not produce those baselines.
- The server has no user authentication, so allow access only to trusted team devices through Tailscale.
- Always stop the server with Ctrl+C before backing up or manually replacing the SQLite files.
- The MacBook must not sleep during training. You can use `caffeinate -i` in another Terminal window for the demo and stop it with Ctrl+C afterward.
