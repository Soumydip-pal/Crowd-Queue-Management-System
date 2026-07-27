"""
CCTV / IP camera poller for continuous crowd counting.

Unlike the browser-based LiveCameraWidget (which only runs while a dashboard
tab is open), this script is meant to run as a small standalone background
process - on a server, a Raspberry Pi, or any machine on the same network as
your CCTV/IP cameras - and keeps counting for as long as it's running.

How it works:
    1. Opens one or more RTSP/HTTP camera streams with OpenCV.
    2. Every POLL_INTERVAL_SECONDS, grabs the latest frame from each camera.
    3. Runs the same OpenCV HOG people detector used by /ml/detect-crowd
       (imported directly from app.py - no HTTP round-trip needed since this
       script runs alongside/near the ML service).
    4. POSTs the resulting count to the backend as a CAMERA-sourced queue
       snapshot via POST /api/camera/count, reusing the exact same pipeline
       the browser widget and single-photo upload use - so all three camera
       input methods show up identically on the live dashboard.
    5. Logs in once with a service account and refreshes the JWT if it
       expires (JWT_EXPIRATION_MINUTES, default 1440 = 24h).

Configuration (environment variables):
    BACKEND_URL              e.g. http://localhost:8080/api (required)
    SERVICE_EMAIL             an ADMIN or MANAGER account's email (required)
    SERVICE_PASSWORD          that account's password (required)
    CAMERAS                   JSON list of {"counter_id": <int>, "rtsp_url": "..."}
                              e.g. CAMERAS='[{"counter_id":1,"rtsp_url":"rtsp://user:pass@192.168.1.50:554/stream1"}]'
                              A plain webcam/USB camera also works: use "0"
                              (or "1", "2"...) as rtsp_url to open local camera index 0.
    POLL_INTERVAL_SECONDS     default 15
    ROI                       optional JSON polygon shared by all cameras,
                              e.g. ROI='[[0.1,0.1],[0.9,0.1],[0.9,0.9],[0.1,0.9]]'

Usage:
    export BACKEND_URL=http://localhost:8080/api
    export SERVICE_EMAIL=admin@example.com
    export SERVICE_PASSWORD=admin123
    export CAMERAS='[{"counter_id":1,"rtsp_url":"rtsp://192.168.1.50:554/stream1"}]'
    python camera_poller.py

Run it as a systemd service / Docker container / Windows Task Scheduler job
for it to survive reboots - it's a plain long-running Python process, no
special infrastructure required.
"""

import json
import os
import sys
import time
from datetime import datetime, timezone

import cv2
import numpy as np
import requests

from app import get_hog_detector, _point_in_polygon  # reuse the exact same detector

BACKEND_URL = os.environ.get("BACKEND_URL", "http://localhost:8080/api")
SERVICE_EMAIL = os.environ.get("SERVICE_EMAIL")
SERVICE_PASSWORD = os.environ.get("SERVICE_PASSWORD")
POLL_INTERVAL_SECONDS = int(os.environ.get("POLL_INTERVAL_SECONDS", "15"))
ROI = json.loads(os.environ["ROI"]) if os.environ.get("ROI") else None

try:
    CAMERAS = json.loads(os.environ["CAMERAS"])
except (KeyError, json.JSONDecodeError) as exc:
    print(f"CAMERAS env var must be a JSON list, e.g. "
          f'CAMERAS=\'[{{"counter_id":1,"rtsp_url":"rtsp://..."}}]\'  ({exc})')
    sys.exit(1)

if not SERVICE_EMAIL or not SERVICE_PASSWORD:
    print("SERVICE_EMAIL and SERVICE_PASSWORD env vars are required "
          "(an ADMIN or MANAGER account - camera counts are write operations).")
    sys.exit(1)


class BackendSession:
    """Holds a JWT and re-logs-in automatically if a request comes back 401."""

    def __init__(self):
        self.token = None

    def login(self):
        response = requests.post(
            f"{BACKEND_URL}/auth/login",
            json={"email": SERVICE_EMAIL, "password": SERVICE_PASSWORD},
            timeout=10,
        )
        response.raise_for_status()
        self.token = response.json()["accessToken"]
        print(f"[{now()}] Logged in as {SERVICE_EMAIL}")

    def submit_count(self, counter_id, count, jpeg_bytes):
        if self.token is None:
            self.login()

        files = {"image": ("frame.jpg", jpeg_bytes, "image/jpeg")}
        data = {}
        if ROI:
            data["roi"] = json.dumps(ROI)

        response = requests.post(
            f"{BACKEND_URL}/camera/count",
            params={"counterId": counter_id},
            files=files,
            data=data,
            headers={"Authorization": f"Bearer {self.token}"},
            timeout=15,
        )
        if response.status_code == 401:
            print(f"[{now()}] Token expired, re-authenticating...")
            self.login()
            response = requests.post(
                f"{BACKEND_URL}/camera/count",
                params={"counterId": counter_id},
                files=files,
                data=data,
                headers={"Authorization": f"Bearer {self.token}"},
                timeout=15,
            )
        response.raise_for_status()
        return response.json()


def now():
    return datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")


def detect_people_count(frame):
    """Runs the same HOG detector as /ml/detect-crowd, in-process (no HTTP hop)."""
    height, width = frame.shape[:2]
    scale = min(1.0, 640 / width) if width > 640 else 1.0
    if scale < 1.0:
        frame = cv2.resize(frame, (int(width * scale), int(height * scale)))

    hog = get_hog_detector()
    boxes, weights = hog.detectMultiScale(frame, winStride=(8, 8), padding=(8, 8), scale=1.05)

    frame_h, frame_w = frame.shape[:2]
    count = 0
    for (x, y, w, h) in boxes:
        cx, cy = (x + w / 2) / frame_w, (y + h / 2) / frame_h
        if ROI and not _point_in_polygon(cx, cy, ROI):
            continue
        count += 1
    return count


def open_camera(rtsp_url):
    # Plain integers ("0", "1"...) open a local/USB webcam by index instead
    # of a network stream - handy for testing this script without real CCTV.
    source = int(rtsp_url) if rtsp_url.isdigit() else rtsp_url
    capture = cv2.VideoCapture(source)
    if not capture.isOpened():
        print(f"[{now()}] WARNING: could not open camera source: {rtsp_url}")
    return capture


def main():
    session = BackendSession()
    session.login()

    captures = {}
    for camera in CAMERAS:
        captures[camera["counter_id"]] = open_camera(camera["rtsp_url"])

    print(f"[{now()}] Polling {len(captures)} camera(s) every {POLL_INTERVAL_SECONDS}s. Ctrl+C to stop.")

    try:
        while True:
            for counter_id, capture in captures.items():
                if not capture.isOpened():
                    # Try to reconnect - RTSP streams commonly drop and need reopening.
                    rtsp_url = next(c["rtsp_url"] for c in CAMERAS if c["counter_id"] == counter_id)
                    captures[counter_id] = open_camera(rtsp_url)
                    continue

                ok, frame = capture.read()
                if not ok or frame is None:
                    print(f"[{now()}] counter {counter_id}: failed to grab frame, will retry")
                    continue

                count = detect_people_count(frame)
                success, buffer = cv2.imencode(".jpg", frame, [cv2.IMWRITE_JPEG_QUALITY, 85])
                if not success:
                    continue

                try:
                    result = session.submit_count(counter_id, count, buffer.tobytes())
                    print(f"[{now()}] counter {counter_id}: {count} people detected, "
                          f"status={result.get('status')}")
                except requests.RequestException as exc:
                    print(f"[{now()}] counter {counter_id}: failed to submit count ({exc})")

            time.sleep(POLL_INTERVAL_SECONDS)
    except KeyboardInterrupt:
        print(f"[{now()}] Stopping...")
    finally:
        for capture in captures.values():
            capture.release()


if __name__ == "__main__":
    main()
