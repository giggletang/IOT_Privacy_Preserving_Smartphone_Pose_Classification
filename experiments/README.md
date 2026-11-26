# Evaluation and experiments

All numbers in this document use **Client A** and **Client B** handset exports in `../private-data/`, not older preliminary CSV recordings. Both models and server snapshots are from the same completed `GRAV_NORM` experiment. The instructor's on-site blind test is **not** included in these measurements.

## Evaluation protocol

- Six poses, labels 0–5: Screen Up, Screen Down, Portrait Up, Portrait Down, Landscape Left (**left side edge up**), Landscape Right (**right side edge up**).
- Client A: train on 0/1/2 (180 windows); Client B: train on 3/4/5 (184 windows). Each client's `test` covers all six poses. No test/calibration samples were used to train.
- MLP: 6 inputs → 16 ReLU hidden units → 6 output logits/softmax, 214 parameters. Independent local-only baselines: 30 epochs; FedAvg: 3 local epochs per client/round, SGD learning rate 0.05, sample-weighted aggregation.
- `GRAV_NORM` features: normalized mean gravity plus `(mean_accel - mean_gravity)/9.81`. `BASELINE`: `mean_accel/9.81` plus `mean_gravity/9.81` (both have 6 dimensions).
- End-of-training server model round 21 corresponds to **21 completed aggregation steps** with paired A and B uploads for rounds 0–20. Phone model snapshots agree with that saved round.

## Smartphone GRAV_NORM results on all six Test poses

| Model | Evaluated device | Correct / N | Accuracy | Macro-F1 |
|---|---|---:|---:|---:|
| Local A | A | 184 / 364 | 50.55% | 0.371 |
| Local B | B | 180 / 361 | 49.86% | 0.357 |
| Global round 21 | A | 364 / 364 | **100.00%** | 1.000 |
| Global round 21 | B | 361 / 361 | **100.00%** | 1.000 |

Local models achieve full recognition on their own three training classes and near-zero recognition on the other client's held-out classes. Six-class overall local accuracy ~50% is an *expected outcome of the intentional non-IID split*, not the metric the instructor uses for on-site local scoring.

## Global robustness: calibration conditions

| Condition | A correct / N | B correct / N | Pooled |
|---|---:|---:|---:|
| Standard (`cal01`) | 122/122 | 119/119 | 241/241 |
| Tilt (`tilt01`) | 120/120 | 120/120 | 240/240 |
| Gentle shake (`shake01`) | 126/126 | 127/127 | 253/253 |

All rows are 100% accuracy and macro-F1 1.000 **under the collected conditions only**. Calibration windows are held under split `calib`; the Android Evaluate buttons read `test` only. These calibration numbers were calculated offline from the saved weights and raw sensor statistics.

## Feature ablation (separate offline experiment)

We **independently retrained** BASELINE and GRAV_NORM models offline with the same input recordings, initial weights, fixed training schedule, split assignments, and FedAvg implementation. The offline GRAV_NORM replay closely reproduced the live round-21 model (max parameter difference ~3.6e-7). BASELINE was **not** trained on the live Android/SQLite deployment.

| Model | Test set | BASELINE accuracy | GRAV_NORM accuracy |
|---|---|---:|---:|
| Local A | A: 364 | 50.55% | 50.55% |
| Local B | B: 361 | 49.86% | 49.86% |
| Global | A: 364 | 100.00% | 100.00% |
| Global | B: 361 | 100.00% | 100.00% |

Both feature configurations achieved 100% global Test accuracy on 725/725 app-readable Test windows, and 100% global accuracy in the Standard (241/241), Tilt (240/240), and Shake (253/253) calibration windows. No measured accuracy or robustness advantage of GRAV_NORM over BASELINE is supported by this dataset.

## FedAvg rounds (retrospective evaluation)

With the **same saved Test windows**, both live GRAV_NORM and offline BASELINE global models reached 100% Test accuracy at **server round 1** and remained there through round 21. Model parameters can continue to evolve even when this accuracy is saturated. Round 10 and Round 20 did **not** have distinct measured accuracy on this dataset. Full per-round metrics: `results/fedavg_rounds.csv` and `results/round_accuracy_all.csv`.

## Data quality and limitations

- Client A has 912 valid CSV rows (180 train, 364 test, 368 calibration). Client B has 911 valid rows (184 train, 361 test, 366 calibration).
- One physical Client B CSV line concatenates a Test and Calibration row (33 fields); Android ignores that line. We excluded it in *all* reported metrics for consistency rather than altering samples.
- Some Client B train timestamps were previously rounded by spreadsheet software. Model features use sensor values, not timestamps. This does not restore precise acquisition chronology.
- Test windows are collected sessions, not instructor-observed blind placements. Results on new devices, new sites, long-duration motion, and boundary-case poses remain unmeasured.
- Standard, tilt, shake conditions are operational descriptions rather than independently calibrated motion magnitudes.
- Perfect accuracy leaves little statistical power to distinguish feature representations; additional challenging blind data would be needed for stronger conclusions.

## How to reproduce the feature comparison locally

From the repository root, keep the supplied `private-data/` directory (ignored by Git). Set up a Python environment with `numpy` and `numba`:

```bash
python3 -m venv .venv
source .venv/bin/activate
python -m pip install -r experiments/requirements.txt
python experiments/run_ablation.py
```

This script reads `private-data/client_A/A_windows.csv`, `private-data/client_B/B_windows.csv`, and `private-data/server/posefl_round20_backup.sqlite3` in read-only mode. It writes output CSVs to `experiments/`; the shipped reference results are in `experiments/results/`. It does **not** change phone models or live server weights. Python and Java floating-point arithmetic are not guaranteed to be bit-identical, but the replay difference is documented.

See `results/` for detailed metrics and full confusion matrices.
