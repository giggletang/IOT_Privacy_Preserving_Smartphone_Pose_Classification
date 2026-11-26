# PoseFL — Privacy-Preserving Smartphone Pose Classification with Federated Learning

**CSC 8223 course project** · **Team:** Yateeka Goyal and Zihan Tang · **Mobile clients:** Client A and Client B

PoseFL is a two-device Android sensing system for classifying six static phone orientations with a small 6→16→6 MLP. Each handset trains from its own local windows; a Python FastAPI coordinator combines parameter updates by sample-weighted FedAvg. The system does **not** upload raw sensor windows or engineered sensor features to the coordinator.

## Repository map

```text
PoseFL/
├── android-app/             Android Studio + Gradle application
│   ├── app/                Kotlin/Jetpack Compose, sensors, on-device MLP
│   └── README.md
├── server/                  FastAPI, FedAvg, SQLite, unit tests, Mac scripts
│   ├── server.py
│   └── README.md
├── experiments/             Evaluation protocol, reproducible offline ablation
│   ├── results/            Aggregate metric CSVs, full confusion matrices
│   ├── run_ablation.py
│   └── README.md
├── docs/                    Final IEEE report (PDF and LaTeX)
├── private-data/            Local experiment artifacts (GIT-IGNORED)
└── .gitignore
```

**Android Studio:** open `android-app/` rather than the repo root. **FastAPI:** run in `server/`. Complete procedures and caveats are in each component's README.

## Authors and responsibilities

| Contributor | Work |
|---|---|
| **Yateeka** | Android application, Jetpack Compose UI, sensors/window capture, local feature extraction, on-device training and inference, integration |
| **Zihan** | Python coordinator, FastAPI endpoints, sample-weighted FedAvg, SQLite persistence, network integration, model validation, statistical evaluation, feature-ablation reproduction, reporting |
| **Both** | Integration, phone data collection, testing, demonstration and report |


## Pose labels and client partitions

| Label | Pose | Physical definition | Client training subset |
|---|---|---|---|
| 0 | Screen Up | Phone flat; screen faces upward | A |
| 1 | Screen Down | Phone flat; screen faces downward | A |
| 2 | Portrait Up | Phone vertical; top edge upward | A |
| 3 | Portrait Down | Phone vertical; top edge downward | B |
| 4 | Landscape Left | Phone vertical; **left side edge upward**, top edge right | B |
| 5 | Landscape Right | Phone vertical; **right side edge upward**, top edge left | B |

Both test sets contain all six labels. The separate `calib` split contains Standard, Tilt, and gentle Shake experiments and is **not used in local/FedAvg training**.

## System, training, and network

1. Phones collect accelerometer and gravity signals and summarize into ~1-second windows. Feature configuration for live FL is `GRAV_NORM` (six dimensions).
2. Each phone trains an **independent local-only** model (30 epochs) using its own three-class local dataset; these are not initialized from the global model.
3. For each FedAvg step, both clients request the latest 214-weight global model (`GET /model`), train it for three epochs locally, then submit `client_id`, `round`, `n_samples`, and 214 trained weights (`POST /update`).
4. When both client updates are received for the same round, the Mac server uses sample-weighted averaging and persists the next round in SQLite.
5. The global model is downloaded and evaluated locally against six-pose Test windows on **both** clients. Raw windows never pass through the FL API.

**Connection:** Install Tailscale on both Android devices and the Mac server. Start `server/` using its README; configure the two clients with the Mac's actual Tailscale IP. The included Android network security config contains an **example** IP that must be adjusted before build. Restrict the experimental HTTP server to trusted tailnet members; do not open it to the public Internet.

## Final dataset (app-valid windows)

| Partition | Client A | Client B |
|---|---:|---:|
| `train` | 180 | 184 |
| `test` | 364 | 361 |
| `calib` | 368 | 366 |
| **Total** | **912** | **911** |

These are data from **Client A and Client B used in the final experiments**.

## Core evaluation (live GRAV_NORM models; server round 21)

| Model | Test device | Correct / N | Accuracy | Macro-F1 |
|---|---|---:|---:|---:|
| Local A | Client A | 184 / 364 | **50.55%** | 0.371 |
| Local B | Client B | 180 / 361 | **49.86%** | 0.357 |
| FedAvg Global | Client A | 364 / 364 | **100.00%** | 1.000 |
| FedAvg Global | Client B | 361 / 361 | **100.00%** | 1.000 |

Independent local models each trained only on three labels and are intentionally weak on the other client's three classes. The saved server is at **round 21**, meaning **21 completed aggregation steps** (paired submissions for rounds 0–20). The testing results above are *not* scores from the instructor's separate live 36-placement blind test.

## Robustness and feature ablation

| Condition | Client A | Client B | Global accuracy, pooled |
|---|---:|---:|---:|
| Standard (`cal01`) | 122 / 122 | 119 / 119 | 241 / 241 (100%) |
| Tilt (`tilt01`) | 120 / 120 | 120 / 120 | 240 / 240 (100%) |
| Gentle Shake (`shake01`) | 126 / 126 | 127 / 127 | 253 / 253 (100%) |

An independent **offline** study retrained BASELINE and GRAV_NORM features on exactly the same client Train/Test splits, hyperparameters, and 21-round FedAvg schedule. Both achieved **100% Global test accuracy (725 / 725)** and perfect recorded Standard/Tilt/Shake accuracy; neither demonstrated a measured advantage. Both reached 100% on the current stored Test sets by **round 1**, with no accuracy improvement from round 10 to 20. Details, raw aggregate tables, confusion matrices and the replay script are in [`experiments/`](experiments/README.md).

## Run and reproduce

**Android:** open [`android-app/`](android-app/) in Android Studio, build/install, collect data, train Local on the correct split, and select correct Client ID.

**Server (macOS):**

```bash
cd server
./setup_mac.sh
./run_tests_mac.sh
./start_tailscale_mac.sh
```

**Offline comparison:**

```bash
python3 -m venv .venv
source .venv/bin/activate
python -m pip install -r experiments/requirements.txt
python experiments/run_ablation.py
```

The experiment script requires the original files from `private-data/`, which are **included in the downloaded package but intentionally excluded from Git tracking**. See [`docs/DATA_ACCESS.md`](docs/DATA_ACCESS.md). An ordinary public GitHub clone has only the aggregate results and reproducibility code, not the handset recordings.

## Final report and limitations

The IEEE-style final write-up is in [`docs/PoseFL_Final_Report.pdf`](docs/PoseFL_Final_Report.pdf), with editable LaTeX at [`docs/PoseFL_Final_Report.tex`](docs/PoseFL_Final_Report.tex). The 100% measurements apply to the saved, team-collected windows and the recorded mild robustness conditions, not untested devices or professor-administered blind placements. No claim of an accuracy benefit from normalization is supported in this dataset.

**For Demo Day:** have both phones and the server ready; be able to show model-weight updates without raw sensor windows; demonstrate independent Local A, Local B and Global predictions. The current app supports toggling Local/Global on a handset; three-model simultaneous recording for all blind placements should be dry-run and confirmed separately.
