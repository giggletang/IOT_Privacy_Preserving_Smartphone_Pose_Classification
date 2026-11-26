# PoseFL Federated Coordinator

Python **FastAPI + SQLite** coordinator and weighted **FedAvg**, developed principally by **Zihan Tang**. Runs locally on a Mac and accepts model updates from Client A and Client B over a private Tailscale network. Sensor windows are **not transmitted** to the server.

## macOS quick start

```bash
cd server
./setup_mac.sh
./run_tests_mac.sh
./start_tailscale_mac.sh
```

The start script prompts for this Mac's `100.x.y.z` Tailscale IPv4, then binds port `8000`. The two Android clients must use the same server URL and distinct Client IDs `A` and `B`. Alternatively `./start_local_mac.sh` runs for local-only testing. Only one process should bind port 8000.

## API

| Endpoint | Function |
|---|---|
| `GET /health` | Server status and `GRAV_NORM` feature configuration |
| `GET /model` | Latest `round` and 214-element `weights` |
| `GET /status` | Aggregation status and clients awaited |
| `POST /update` | Accept `{client_id, round, n_samples, weights}`; aggregate when both clients submit for the same round |
| `GET /docs` | FastAPI OpenAPI interactive reference |

`POST /update` carries model weights, never raw gravity/acceleration windows or six-feature vectors. The FedAvg server tracks 6→16→6 MLP weights in W1, b1, W2, b2 order (214 floats). Server-side storage is in `server/data/posefl.sqlite3` by default, or `POSEFL_DB_PATH` if set. This runtime database is excluded from Git.

## Security, replication, and history

This teaching prototype has **no HTTP authentication or transport encryption by itself**. Restrict it to an authorized Tailscale tailnet, do not expose port 8000 publicly, and do not upload real Tailscale IPs/credentials. The repository includes the code and **anonymized aggregate** evaluation tables; original mobile windows and server SQLite snapshots are kept under ignored `private-data/` for local analyses only.

Historical results in [`../experiments/`](../experiments/) summarize **21 completed aggregations**, reflected as server `round 21` (two client updates per round, from rounds 0 through 20). An extra training command would advance beyond this recorded experiment. Do not mix BASELINE and GRAV_NORM uploads in the same live database; BASELINE was trained independently **offline**, not on this server.

Run `./run_tests_mac.sh` for server API/FedAvg unit tests. See root [`README.md`](../README.md).
