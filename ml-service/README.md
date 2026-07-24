# ML Service

Flask service for queue wait-time prediction.

## Run

```bash
python app.py
```

The service runs on `http://localhost:5001`.

## Endpoints

### `GET /health`

Returns service status and model version.

### `POST /ml/predict`

Request:

```json
{
  "counter_id": 1,
  "current_length": 42,
  "service_rate_per_hour": 30,
  "arrival_rate_per_hour": 8,
  "time_of_day": 14,
  "day_of_week": 3
}
```

Response:

```json
{
  "predicted_wait_min": 91,
  "status": "Overcrowded",
  "confidence": 0.72,
  "model_version": "baseline-queue-v1",
  "best_slot_today": []
}
```

This is a deterministic baseline model. A trained scikit-learn model can later replace the internal `baseline_wait_minutes` function while keeping the same API contract.

## Optional Computer Vision Dependencies

YOLO/OpenCV dependencies are listed separately in `requirements-cv.txt` because they are large and not required for the baseline prediction service:

```bash
pip install -r requirements-cv.txt
```
