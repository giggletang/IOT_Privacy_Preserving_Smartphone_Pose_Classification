OPTIONAL ANDROID APP CHANGE (AFTER TESTING TAILSCALE IN THE BROWSER)

The team-provided Android app currently contains a network_security_config.xml
with the placeholder domain 100.x.y.z. Changing only the Server URL inside
the app is not enough; Android can reject HTTP to your real Tailscale IP.

Option 1 - narrow scope (preferred):
Replace the placeholder 100.x.y.z domain in the ORIGINAL file with the real
Tailscale IPv4 of the Windows server. No other changes needed.

Option 2 - easiest development-only replacement:
Replace the COMPLETE file at:
  app/src/main/res/xml/network_security_config.xml
with the provided network_security_config_DEV_ONLY.xml (rename it to
network_security_config.xml). This allows cleartext HTTP to all domains in this
app. Use only for trusted coursework testing, with the server bound to Tailscale.
Prefer more restrictive settings or HTTPS for production use.

App settings on both phones:
  Server URL: http://YOUR_ACTUAL_PC_TAILSCALE_IP:8000
  Client ID: A on teammate phone, B on your phone
  Features: GRAV_NORM on BOTH phones

Before final training, resolve landscape labels against instructor definitions.
