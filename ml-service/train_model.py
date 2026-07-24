"""
Trains a real wait-time prediction model for the crowd management system.

Why synthetic data:
    The project has no historical queue logs yet (fresh deployment). Instead of
    shipping a random/deterministic-formula "model", this script generates a
    large, realistic synthetic dataset grounded in queueing theory (a noisy
    M/M/1-style relationship between queue length, service rate, arrival rate,
    time-of-day and day-of-week), then trains a real scikit-learn
    RandomForestRegressor on it.

    Once the system has been running in production and has accumulated real
    QueueSnapshot / Prediction history (exportable via
    GET /api/analytics/export.csv), re-run this script pointing at that CSV
    (see --real-data) to retrain on actual observed data instead of the
    synthetic bootstrap set. The feature contract (see FEATURE_COLUMNS) stays
    the same either way, so app.py does not need to change.

Usage:
    python train_model.py                  # train on synthetic bootstrap data
    python train_model.py --real-data history.csv   # train on exported history

Output:
    model.pkl        - joblib-serialized sklearn Pipeline (scaler + RF regressor)
    model_meta.json  - version string, feature list, training metrics
"""

import argparse
import json
import math
import random
from datetime import datetime, timezone

import joblib
import numpy as np
import pandas as pd
from sklearn.ensemble import RandomForestRegressor
from sklearn.metrics import mean_absolute_error, r2_score
from sklearn.model_selection import train_test_split
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler

MODEL_VERSION = "rf-wait-predictor-v1"
FEATURE_COLUMNS = [
    "current_length",
    "service_rate_per_hour",
    "arrival_rate_per_hour",
    "time_of_day",
    "day_of_week",
    "is_weekend",
]
MODEL_PATH = "model.pkl"
META_PATH = "model_meta.json"


def generate_synthetic_dataset(n_rows: int = 20000, seed: int = 42) -> pd.DataFrame:
    """Generate a synthetic but queueing-theory-grounded training set.

    Ground-truth wait time follows an M/M/1-ish formula:
        effective_service_rate = service_rate - 0.35 * arrival_rate  (congestion effect)
        wait_minutes = 60 * current_length / effective_service_rate

    On top of that we layer:
      - a rush-hour multiplier (lunchtime / evening peaks take longer than the
        raw formula predicts, because of real-world friction: cashiers slow
        down, extra verification steps, etc.)
      - weekend effect (small extra slowdown, more casual/large transactions)
      - Gaussian noise, so the model has to generalize rather than memorize
        a closed-form formula exactly.
    """
    rng = np.random.default_rng(seed)
    random.seed(seed)

    rows = []
    for _ in range(n_rows):
        # Ranges tuned to realistic single-counter service scenarios (ticket
        # counters, checkout lines, immigration desks): 5-40 people served per
        # hour, queue length capped at 100 waiting people.
        current_length = rng.integers(0, 100)
        service_rate = rng.uniform(5, 40)
        arrival_rate = rng.uniform(0, service_rate * 1.2)
        time_of_day = rng.integers(0, 24)
        day_of_week = rng.integers(1, 8)  # 1=Mon .. 7=Sun
        is_weekend = 1 if day_of_week in (6, 7) else 0

        effective_service_rate = max(service_rate - 0.35 * arrival_rate, 2.0)
        base_wait = 60.0 * current_length / effective_service_rate

        rush_hour = time_of_day in (8, 9, 12, 13, 17, 18, 19)
        rush_multiplier = rng.uniform(1.15, 1.45) if rush_hour else rng.uniform(0.9, 1.05)
        weekend_multiplier = rng.uniform(1.0, 1.15) if is_weekend else 1.0

        noise = rng.normal(loc=0, scale=max(1.5, base_wait * 0.08))
        # Real-world wait times realistically cap out (people abandon queues,
        # counters open extra windows, staff reallocate) so clip at 180 min.
        wait_minutes = min(180.0, max(0.0, base_wait * rush_multiplier * weekend_multiplier + noise))

        rows.append({
            "current_length": current_length,
            "service_rate_per_hour": round(service_rate, 2),
            "arrival_rate_per_hour": round(arrival_rate, 2),
            "time_of_day": time_of_day,
            "day_of_week": day_of_week,
            "is_weekend": is_weekend,
            "wait_minutes": round(wait_minutes, 2),
        })

    return pd.DataFrame(rows)


def load_real_dataset(csv_path: str) -> pd.DataFrame:
    """Load an exported queue-history CSV (from /api/analytics/export.csv) and
    reshape it into the same feature/target schema as the synthetic set.

    NOTE: the exported CSV only has current_length + timestamp + source, not
    service_rate/arrival_rate/wait target directly, so a real production
    fine-tuning pass should join QueueSnapshot with Counter.service_rate and
    Prediction.predicted_wait_min server-side before exporting. This loader
    expects a CSV with at least the FEATURE_COLUMNS + wait_minutes columns.
    """
    df = pd.read_csv(csv_path)
    missing = set(FEATURE_COLUMNS + ["wait_minutes"]) - set(df.columns)
    if missing:
        raise ValueError(
            f"Real-data CSV is missing required columns: {sorted(missing)}. "
            "Export a dataset with these columns (current_length, "
            "service_rate_per_hour, arrival_rate_per_hour, time_of_day, "
            "day_of_week, is_weekend, wait_minutes) before retraining."
        )
    return df


def train(df: pd.DataFrame):
    X = df[FEATURE_COLUMNS]
    y = df["wait_minutes"]
    X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42)

    pipeline = Pipeline([
        ("scaler", StandardScaler()),
        ("model", RandomForestRegressor(
            n_estimators=200,
            max_depth=14,
            min_samples_leaf=3,
            random_state=42,
            n_jobs=-1,
        )),
    ])
    pipeline.fit(X_train, y_train)

    predictions = pipeline.predict(X_test)
    mae = mean_absolute_error(y_test, predictions)
    r2 = r2_score(y_test, predictions)

    return pipeline, {"mae_minutes": round(float(mae), 3), "r2_score": round(float(r2), 4), "n_rows": len(df)}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--real-data", type=str, default=None, help="Path to exported queue-history CSV")
    parser.add_argument("--rows", type=int, default=20000, help="Synthetic rows to generate")
    args = parser.parse_args()

    if args.real_data:
        print(f"Training on real exported data: {args.real_data}")
        df = load_real_dataset(args.real_data)
    else:
        print(f"Training on synthetic bootstrap data ({args.rows} rows)")
        df = generate_synthetic_dataset(args.rows)

    pipeline, metrics = train(df)

    joblib.dump(pipeline, MODEL_PATH)
    meta = {
        "model_version": MODEL_VERSION,
        "feature_columns": FEATURE_COLUMNS,
        "trained_at": datetime.now(timezone.utc).isoformat(),
        "trained_on": "real-export" if args.real_data else "synthetic-bootstrap",
        "metrics": metrics,
    }
    with open(META_PATH, "w") as f:
        json.dump(meta, f, indent=2)

    print(f"Saved {MODEL_PATH} and {META_PATH}")
    print(json.dumps(meta, indent=2))


if __name__ == "__main__":
    main()
