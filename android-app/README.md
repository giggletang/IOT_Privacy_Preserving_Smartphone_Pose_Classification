# PoseFL Android App

Android Studio project for the **Client A** and **Client B** mobile devices. Open **this directory** (`android-app/`) in Android Studio; it contains the Gradle root and the `app/` module.

## Ownership and design

Mobile UI, sensor measurement, 1-second window extraction, local MLP training, model inference, CSV export, and FL HTTP client were developed principally by **Yateeka Goyal**; integration and validation were completed with **Zihan Tang**. The same app code runs on both clients, configured with distinct identifiers `A` and `B`.

| Client | Local training classes | Test/calibration classes |
|---|---|---|
| Client A | 0 Screen Up, 1 Screen Down, 2 Portrait Up | All six |
| Client B | 3 Portrait Down, 4 Landscape Left, 5 Landscape Right | All six |

Landscape Left means **left side edge up** (top edge right). Landscape Right means **right side edge up** (top edge left). This follows the instructor's pose cards.

## Start

1. Open `android-app/` in Android Studio and allow Gradle to sync. Build/install the `app` on each Android phone.
2. Both phones must reach the same trusted Tailscale network as the server. The server defaults to `GRAV_NORM`.
3. **Before building**, edit `app/src/main/res/xml/network_security_config.xml`: replace the example `100.101.20.30` with **the server Mac's actual Tailscale IPv4** (or configure a more secure HTTPS transport). HTTP cleartext is permitted only for the configured address. Do not publish a real private Tailscale address.
4. In **Train**, set Server URL to `http://SERVER_TAILSCALE_IP:8000`. Set Client ID to `A` on Client A and `B` on Client B.
5. Collect using split `train` for local updates, `test` for six-class evaluation, and `calib` for Standard/Tilt/Shake robustness analysis.
6. `Train Local Model` makes an independent model from local `train` windows (30 epochs). `Run FL Round` downloads the global weights, does 3 local epochs, and sends 214 weights plus the client ID, round, and sample count. Each client uploads once per communication round; the server aggregates when both are present.
7. `Refresh Global Model` saves the server's current global weights; **Infer** and Evaluate use saved local/global weights.

## Client storage

Android's private application files directory (`/data/data/com.csc8223.posefl/files/`) stores `windows.csv`, `model_local.txt`, `model_global.txt`. The full source `windows.csv` has 17 CSV fields per valid window. `test` and `calib` are excluded from training. The App's `Evaluate` actions consume `test` only; calibrations were evaluated offline using `experiments/`. Evaluation messages in the Activity Log are **not** exported automatically: capture screenshots if needed.

The App's `Clear Data` deletes the **whole** `windows.csv`, not just Test windows. Back up all data before clearing or editing. Avoid Excel: it can transform integer timestamps to scientific notation, making CSV rows unreadable by the Android parser.

## Build check

From `android-app/`: `./gradlew :app:assembleDebug` (requires an installed compatible Android SDK/JDK). Local unit tests: `./gradlew :app:testDebugUnitTest`.

## Relevant source files

- `app/src/main/java/com/csc8223/posefl/` — `Features.kt`, `MLP.kt`, `Trainer.kt`, `Storage.kt`, `AppVM.kt`, `FlClient.kt`, `PoseFLScreen.kt`, `Sensors.kt`.
- `app/src/main/res/xml/network_security_config.xml` — configured development-only server address.

See root [`README.md`](../README.md) and [`experiments/README.md`](../experiments/README.md) for real evaluation results. The current project uses the Client A and Client B data documented in the experiment inventory.
