"""CSC 8223 PoseFL - local FastAPI / FedAvg coordinator.

Client contract is intentionally kept exactly compatible with the Android
FlClient.kt supplied by the project team. Sensor samples are never uploaded.
"""
from __future__ import annotations

import json
import math
import os
import random
import sqlite3
import threading
from contextlib import contextmanager
from pathlib import Path
from typing import Literal

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, ConfigDict, Field, field_validator

WEIGHT_COUNT = 214
IN_DIM, HIDDEN_DIM, OUT_DIM = 6, 16, 6
FEATURE_CONFIG = "GRAV_NORM"
CLIENT_IDS = ("A", "B")
DEFAULT_DB = Path(__file__).resolve().parent / "data" / "posefl.sqlite3"


def initial_weights(seed: int = 42) -> list[float]:
    rng = random.Random(seed)
    weights = [0.0] * WEIGHT_COUNT
    s1 = math.sqrt(6.0 / (IN_DIM + HIDDEN_DIM))
    s2 = math.sqrt(6.0 / (HIDDEN_DIM + OUT_DIM))
    for index in range(0, 96):
        weights[index] = rng.uniform(-s1, s1)
    for index in range(112, 208):
        weights[index] = rng.uniform(-s2, s2)
    return weights


class UpdateRequest(BaseModel):
    # Preserve the exact JSON fields expected by FlClient.kt.
    model_config = ConfigDict(extra="forbid")
    client_id: Literal["A", "B"]
    round: int = Field(ge=0)
    n_samples: int = Field(gt=0, le=1_000_000)
    weights: list[float] = Field(min_length=WEIGHT_COUNT, max_length=WEIGHT_COUNT)

    @field_validator("weights")
    @classmethod
    def all_finite(cls, values: list[float]) -> list[float]:
        if not all(math.isfinite(v) for v in values):
            raise ValueError("weights must contain only finite numbers")
        return values


@contextmanager
def db_connection(db_path: Path):
    connection = sqlite3.connect(str(db_path), timeout=30.0, isolation_level=None)
    connection.row_factory = sqlite3.Row
    connection.execute("PRAGMA busy_timeout=30000")
    try:
        yield connection
    finally:
        connection.close()


def initialize_database(db_path: Path) -> None:
    db_path.parent.mkdir(parents=True, exist_ok=True)
    with db_connection(db_path) as db:
        db.execute("PRAGMA journal_mode=WAL")
        db.executescript("""
            CREATE TABLE IF NOT EXISTS global_models (
                round INTEGER PRIMARY KEY,
                weights_json TEXT NOT NULL,
                feature_config TEXT NOT NULL,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            );
            CREATE TABLE IF NOT EXISTS client_updates (
                round INTEGER NOT NULL,
                client_id TEXT NOT NULL CHECK(client_id IN ('A','B')),
                n_samples INTEGER NOT NULL CHECK(n_samples > 0),
                weights_json TEXT NOT NULL,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (round, client_id)
            );
        """)
        db.execute(
            "INSERT OR IGNORE INTO global_models (round, weights_json, feature_config) VALUES (?, ?, ?)",
            (0, json.dumps(initial_weights()), FEATURE_CONFIG),
        )
        configured = db.execute("SELECT DISTINCT feature_config FROM global_models").fetchall()
        if any(row["feature_config"] != FEATURE_CONFIG for row in configured):
            raise RuntimeError("Stored feature configuration does not match this server. Create a new experiment DB.")


def latest_model(db: sqlite3.Connection) -> sqlite3.Row:
    row = db.execute("SELECT round, weights_json FROM global_models ORDER BY round DESC LIMIT 1").fetchone()
    if row is None:
        raise RuntimeError("Global model was not initialized")
    return row


def combine(a: list[float], b: list[float], na: int, nb: int) -> list[float]:
    if len(a) != WEIGHT_COUNT or len(b) != WEIGHT_COUNT or na <= 0 or nb <= 0:
        raise ValueError("Invalid weight vector or sample count")
    denom = na + nb
    result = [(na * x + nb * y) / denom for x, y in zip(a, b)]
    if not all(math.isfinite(v) for v in result):
        raise ValueError("FedAvg produced non-finite weights")
    return result


def create_app(db_path: Path | str | None = None) -> FastAPI:
    selected_db = Path(db_path or os.environ.get("POSEFL_DB_PATH", str(DEFAULT_DB))).resolve()
    initialize_database(selected_db)
    app = FastAPI(title="CSC 8223 PoseFL Coordinator", version="1.0.0")
    app.state.db_path = selected_db
    # Serializes requests within one Python process; SQLite BEGIN IMMEDIATE
    # also prevents transaction races across processes.
    write_lock = threading.Lock()

    @app.get("/health")
    def health():
        return {"status": "ok", "feature_config": FEATURE_CONFIG}

    @app.get("/model")
    def get_model():
        with db_connection(selected_db) as db:
            row = latest_model(db)
            return {"round": row["round"], "weights": json.loads(row["weights_json"])}

    @app.get("/status")
    def get_status():
        with db_connection(selected_db) as db:
            row = latest_model(db)
            clients = db.execute(
                "SELECT client_id FROM client_updates WHERE round=? ORDER BY client_id", (row["round"],)
            ).fetchall()
            uploaded = [c["client_id"] for c in clients]
            return {
                "round": row["round"],
                "feature_config": FEATURE_CONFIG,
                "uploaded_clients": uploaded,
                "waiting_for": [c for c in CLIENT_IDS if c not in uploaded],
                "ready_to_train": len(uploaded) == 0,
            }

    @app.post("/update")
    def post_update(payload: UpdateRequest):
        with write_lock, db_connection(selected_db) as db:
            try:
                db.execute("BEGIN IMMEDIATE")
                model = latest_model(db)
                current_round = model["round"]
                if payload.round != current_round:
                    db.execute("ROLLBACK")
                    raise HTTPException(status_code=409, detail={
                        "message": "Round mismatch: download /model and try the latest round",
                        "current_round": current_round,
                        "submitted_round": payload.round,
                    })

                existing = db.execute(
                    "SELECT n_samples, weights_json FROM client_updates WHERE round=? AND client_id=?",
                    (current_round, payload.client_id),
                ).fetchone()
                if existing is not None:
                    same = (existing["n_samples"] == payload.n_samples and
                            json.loads(existing["weights_json"]) == payload.weights)
                    db.execute("COMMIT")
                    if same:
                        return {"status": "already_received", "round": current_round,
                                "uploaded_clients": [payload.client_id]}
                    raise HTTPException(status_code=409, detail="This client already submitted a different update for this round")

                db.execute(
                    "INSERT INTO client_updates (round, client_id, n_samples, weights_json) VALUES (?, ?, ?, ?)",
                    (current_round, payload.client_id, payload.n_samples, json.dumps(payload.weights)),
                )
                updates = db.execute(
                    "SELECT client_id, n_samples, weights_json FROM client_updates WHERE round=? ORDER BY client_id",
                    (current_round,),
                ).fetchall()
                if len(updates) < 2:
                    db.execute("COMMIT")
                    return {"status": "waiting_for_other_client", "round": current_round,
                            "uploaded_clients": [u["client_id"] for u in updates]}
                if [u["client_id"] for u in updates] != list(CLIENT_IDS):
                    raise RuntimeError("Unexpected client set")
                a, b = updates
                new_weights = combine(json.loads(a["weights_json"]), json.loads(b["weights_json"]),
                                      a["n_samples"], b["n_samples"])
                db.execute(
                    "INSERT INTO global_models (round, weights_json, feature_config) VALUES (?, ?, ?)",
                    (current_round + 1, json.dumps(new_weights), FEATURE_CONFIG),
                )
                db.execute("COMMIT")
                return {"status": "aggregated", "round": current_round + 1,
                        "uploaded_clients": list(CLIENT_IDS)}
            except HTTPException:
                raise
            except Exception:
                if db.in_transaction:
                    db.execute("ROLLBACK")
                raise

    return app


app = create_app()
