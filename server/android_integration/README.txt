ANDROID CONNECTION (DEVELOPMENT)

Open android-app/ in Android Studio. Configure the two phones with Client IDs A and B; both must use the same trusted server URL, usually http://YOUR_MAC_TAILSCALE_IP:8000.

The app's android-app/app/src/main/res/xml/network_security_config.xml includes a sample Tailscale IP (100.101.20.30), which MUST be replaced with the server's real IP prior to build. Android HTTP cleartext allowances must be limited to a trusted address; never expose port 8000 publicly.

Check GET /health in both phones' browsers; check GET /model to retrieve the latest weights; check /status after each client upload.

Sensor windows, CSV files, and derived feature vectors stay on-device; the Android client POST /update sends model weights and the n_samples count. See both component READMEs.
