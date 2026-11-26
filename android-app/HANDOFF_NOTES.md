# Current integration notes

This is the **current** data/experiment handoff for the app. Use the Client A and Client B recordings described below for all final results.

- **Client A**: train labels 0/1/2; 180 train, 364 test, 368 calibration windows (912 app-valid rows).
- **Client B**: train labels 3/4/5; 184 train, 361 test, 366 calibration windows (911 app-valid rows).
- Distinct phone data and two independent local-only model files.
- `GRAV_NORM` was used for all mobile/local/federated execution. The saved global model is server **round 21**, after 21 completed aggregations (uploads associated with rounds 0–20).
- On the stored Test dataset, Global = 100% on Client A and Client B; Local A = 50.55% on six-class A Test; Local B = 49.86% on six-class B Test.
- Calibration sessions `cal01`, `tilt01`, `shake01` are `calib` (not `test`). Evaluated offline.
- Client B source has **one malformed concatenated CSV line** rejected by the Android parser; reported totals deliberately reflect only the 911 valid original records. Preserve the raw originals.
- BASELINE vs GRAV_NORM was an independent **offline** controlled ablation. Both achieved 100% Global on saved Test data. This does **not** establish superior accuracy of GRAV_NORM.

See [`../experiments/README.md`](../experiments/README.md) for full evaluation conditions, and [`../private-data/README.md`](../private-data/README.md) for the local raw data schema and safe sharing.
