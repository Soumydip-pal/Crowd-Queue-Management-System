from datetime import datetime, timedelta, timezone
import io
import json
import math
import os

import numpy as np
from flask import Flask, jsonify, request
from flask_cors import CORS

app = Flask(__name__)
CORS(app)

BASELINE_MODEL_VERSION = "baseline-queue-v1"
MODEL_PATH = os.path.join(os.path.dirname(__file__), "model.pkl")
META_PATH = os.path.join(os.path.dirname(__file__), "model_meta.json")

# --- Real trained ML model (see train_model.py) -----------------------------
# Loaded once at process start. If model.pkl is missing (e.g. train_model.py
# was never run), we transparently fall back to the deterministic baseline
# formula so the service still works, and report which one served the
# request via `model_version` / `model_source` in the response.
_ml_model = None
_ml_meta = None


def load_model():
    global _ml_model, _ml_meta
    try:
        import joblib
        _ml_model = joblib.load(MODEL_PATH)
        with open(META_PATH) as f:
            _ml_meta = json.load(f)
        app.logger.info("Loaded trained model %s", _ml_meta.get("model_version"))
    except FileNotFoundError:
        _ml_model = None
        _ml_meta = None
        app.logger.warning(
            "model.pkl not found - falling back to baseline formula. "
            "Run `python train_model.py` to train and enable the real model."
        )
    except Exception as exc:  # pragma: no cover - defensive
        _ml_model = None
        _ml_meta = None
        app.logger.error("Failed to load model.pkl (%s) - using baseline formula", exc)


load_model()


# --- Camera / crowd-counting (OpenCV HOG person detector) -------------------
# Lazily constructed - avoids paying descriptor-setup cost when the endpoint
# is never used, and keeps process startup fast.
_hog_detector = None


def get_hog_detector():
    global _hog_detector
    if _hog_detector is None:
        import cv2
        hog = cv2.HOGDescriptor()
        hog.setSVMDetector(cv2.HOGDescriptor_getDefaultPeopleDetector())
        _hog_detector = hog
    return _hog_detector


def clamp_number(value, minimum, default):
    try:
        parsed = float(value)
    except (TypeError, ValueError):
        return default
    return max(minimum, parsed)


def baseline_wait_minutes(current_length, service_rate_per_hour, arrival_rate_per_hour=0):
    if current_length <= 0:
        return 0

    service_rate = clamp_number(service_rate_per_hour, 1, 30)
    arrival_rate = clamp_number(arrival_rate_per_hour, 0, 0)
    effective_service_rate = max(service_rate - (arrival_rate * 0.35), 1)
    minutes_per_person = 60 / effective_service_rate
    return int(math.ceil(current_length * minutes_per_person))


def model_wait_minutes(current_length, service_rate_per_hour, arrival_rate_per_hour, time_of_day, day_of_week):
    """Predict wait time with the trained RandomForest model if available,
    otherwise fall back to the baseline formula. Returns (minutes, model_version)."""
    if _ml_model is None:
        return baseline_wait_minutes(current_length, service_rate_per_hour, arrival_rate_per_hour), BASELINE_MODEL_VERSION

    tod = int(time_of_day) if time_of_day is not None else datetime.now(timezone.utc).hour
    dow = int(day_of_week) if day_of_week is not None else datetime.now(timezone.utc).isoweekday()
    is_weekend = 1 if dow in (6, 7) else 0

    import pandas as pd
    features = pd.DataFrame(
        [[current_length, service_rate_per_hour, arrival_rate_per_hour, tod, dow, is_weekend]],
        columns=["current_length", "service_rate_per_hour", "arrival_rate_per_hour", "time_of_day", "day_of_week", "is_weekend"],
    )
    try:
        predicted = float(_ml_model.predict(features)[0])
        return max(0, int(round(predicted))), _ml_meta.get("model_version", "rf-wait-predictor-v1")
    except Exception as exc:  # pragma: no cover - defensive fallback
        app.logger.error("Model prediction failed (%s) - using baseline formula", exc)
        return baseline_wait_minutes(current_length, service_rate_per_hour, arrival_rate_per_hour), BASELINE_MODEL_VERSION


def risk_status(current_length, predicted_wait_min):
    if current_length >= 80 or predicted_wait_min >= 30:
        return "Overcrowded"
    if current_length >= 50 or predicted_wait_min >= 20:
        return "Busy"
    return "Normal"


def best_slots(current_length, service_rate_per_hour, arrival_rate_per_hour):
    now = datetime.now(timezone.utc)
    slots = []
    for slot in range(1, 25):
        start = now + timedelta(minutes=slot * 15)
        projected_departures = (service_rate_per_hour / 4) * slot
        projected_arrivals = (arrival_rate_per_hour / 4) * slot
        projected_length = max(0, current_length + projected_arrivals - projected_departures)
        predicted, _ = model_wait_minutes(
            projected_length,
            service_rate_per_hour,
            arrival_rate_per_hour,
            start.hour,
            start.isoweekday(),
        )
        slots.append({
            "start": start.isoformat(),
            "predicted_wait_min": predicted,
        })
    return sorted(slots, key=lambda item: item["predicted_wait_min"])[:3]


@app.get("/health")
def health():
    return jsonify({
        "status": "ok",
        "model_version": (_ml_meta or {}).get("model_version", BASELINE_MODEL_VERSION),
        "model_loaded": _ml_model is not None,
    })


@app.post("/ml/predict")
def predict():
    payload = request.get_json(silent=True) or {}
    current_length = clamp_number(payload.get("current_length"), 0, 0)
    service_rate_per_hour = clamp_number(payload.get("service_rate_per_hour"), 1, 30)
    arrival_rate_per_hour = clamp_number(payload.get("arrival_rate_per_hour"), 0, 0)
    time_of_day = payload.get("time_of_day")
    day_of_week = payload.get("day_of_week")

    predicted_wait_min, model_version = model_wait_minutes(
        current_length, service_rate_per_hour, arrival_rate_per_hour, time_of_day, day_of_week
    )

    return jsonify({
        "predicted_wait_min": predicted_wait_min,
        "status": risk_status(current_length, predicted_wait_min),
        "confidence": 0.9 if _ml_model is not None else 0.72,
        "model_version": model_version,
        "best_slot_today": best_slots(
            current_length,
            service_rate_per_hour,
            arrival_rate_per_hour,
        ),
        "features": {
            "current_length": current_length,
            "service_rate_per_hour": service_rate_per_hour,
            "arrival_rate_per_hour": arrival_rate_per_hour,
            "time_of_day": time_of_day,
            "day_of_week": day_of_week,
        },
    })


@app.get("/ml/crowd-count")
def crowd_count_compatibility():
    current_length = clamp_number(request.args.get("currentLength"), 0, 0)
    service_rate_per_hour = clamp_number(request.args.get("serviceRatePerHour"), 1, 30)
    predicted_wait_min, model_version = model_wait_minutes(
        current_length, service_rate_per_hour, 0, None, None
    )
    return jsonify({
        "count": int(current_length),
        "status": risk_status(current_length, predicted_wait_min),
        "predicted_wait_min": predicted_wait_min,
        "model_version": model_version,
    })


@app.post("/ml/detect-crowd")
def detect_crowd():
    """Camera-based crowd counting (Phase 5 of the roadmap).

    Accepts a multipart image upload (field name `image`) and an optional
    JSON `roi` (region of interest) as a normalized polygon, e.g.
    roi=[[0.1,0.1],[0.9,0.1],[0.9,0.9],[0.1,0.9]] with coordinates in [0,1]
    relative to image width/height. Runs an OpenCV HOG person detector,
    filters detections to the ROI if given, and returns the count so the
    backend can POST it to /api/queue with source=CAMERA.

    Note: OpenCV's HOG+SVM detector is used instead of YOLOv8 because it
    ships inside opencv-python with no extra model-weight download, which
    keeps this service deployable offline / in restricted network
    environments. To upgrade to YOLOv8 for higher accuracy, install
    `ultralytics` (see requirements-cv.txt) and swap the implementation of
    this function for a `YOLO(...).predict(...)` call filtered to the
    "person" class - the response contract below does not need to change.
    """
    import cv2

    if "image" not in request.files:
        return jsonify({"error": "multipart field 'image' is required"}), 400

    file_bytes = np.frombuffer(request.files["image"].read(), dtype=np.uint8)
    frame = cv2.imdecode(file_bytes, cv2.IMREAD_COLOR)
    if frame is None:
        return jsonify({"error": "could not decode image"}), 400

    height, width = frame.shape[:2]

    # Downscale very large frames for speed; HOG works fine at ~640px wide.
    scale = min(1.0, 640 / width) if width > 640 else 1.0
    if scale < 1.0:
        frame = cv2.resize(frame, (int(width * scale), int(height * scale)))

    hog = get_hog_detector()
    boxes, weights = hog.detectMultiScale(
        frame, winStride=(8, 8), padding=(8, 8), scale=1.05
    )

    roi_raw = request.form.get("roi")
    roi_polygon = None
    if roi_raw:
        try:
            roi_polygon = json.loads(roi_raw)
        except (TypeError, ValueError):
            roi_polygon = None

    detections = []
    frame_h, frame_w = frame.shape[:2]
    for (x, y, w, h), weight in zip(boxes, weights):
        cx, cy = x + w / 2, y + h / 2
        if roi_polygon and not _point_in_polygon(cx / frame_w, cy / frame_h, roi_polygon):
            continue
        detections.append({
            "x": int(x / scale), "y": int(y / scale),
            "width": int(w / scale), "height": int(h / scale),
            "confidence": round(float(weight), 3),
        })

    count = len(detections)
    return jsonify({
        "count": count,
        "detections": detections,
        "roi_applied": roi_polygon is not None,
        "detector": "opencv-hog-people-detector",
        "image_width": width,
        "image_height": height,
    })


def _point_in_polygon(x, y, polygon):
    """Standard ray-casting point-in-polygon test. polygon: list of [x,y] in
    normalized [0,1] coordinates."""
    inside = False
    n = len(polygon)
    j = n - 1
    for i in range(n):
        xi, yi = polygon[i]
        xj, yj = polygon[j]
        intersects = ((yi > y) != (yj > y)) and (
            x < (xj - xi) * (y - yi) / ((yj - yi) or 1e-9) + xi
        )
        if intersects:
            inside = not inside
        j = i
    return inside


if __name__ == "__main__":
    port = int(os.environ.get("PORT", 5001))
    app.run(host="0.0.0.0", port=port, debug=False)
