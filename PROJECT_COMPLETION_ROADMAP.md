# Crowd Management System - Completion Roadmap

## Selected Tech Stack

- Backend: Java 17, Spring Boot 3, Spring Web, Spring Security, Spring Data JPA, Spring WebSocket
- Frontend: React, preferably Vite for final version, with a dashboard/user/admin UI
- Database: PostgreSQL
- ML service: Python, FastAPI or Flask, scikit-learn/pandas, later YOLOv8/OpenCV for camera counting
- Optional realtime/cache layer: Redis
- Deployment: Docker Compose

## Starting State From Attached Project Zip

- `backend-springboot` exists and exposes `GET /api/crowd-status`.
- Spring Boot calls the Python ML service at `http://localhost:5001/ml/crowd-count`.
- `ml-service` currently returns a random crowd count and status.
- `crowd-dashboard` exists and polls the backend every 3 seconds.
- Auth code exists, but it is demo-level and split across inconsistent packages.
- There is no real database schema, queue history, counter management, prediction storage, WebSocket live flow, reports, alerts, or trained ML model yet.

## Implementation Progress

- Clean source has been extracted into this workspace from `D:\Crowd_Managment_System.zip`.
- Backend Phase 1 has started.
- Spring Boot now has JPA entities, repositories, JWT-style bearer auth, seed data, and first REST APIs for auth, locations, counters, queue snapshots, and baseline prediction.
- Backend Phase 2 has started.
- Spring Boot now has a STOMP WebSocket endpoint at `/ws` and publishes live queue updates to `/topic/counter.{id}.live` and `/topic/location.{id}.live` after `POST /api/queue`.
- React dashboard has been updated to load counters from the backend, fetch current live status, subscribe to WebSocket live updates, and fall back to REST polling.
- Frontend Phase 3 has started.
- React dashboard now includes an admin/operator console with login, saved bearer token, logout, and manual queue-count submission to `POST /api/queue`.
- React dashboard now includes admin management forms to create locations, create counters, and update selected counter status/service rate.
- Alert subscription work has started.
- Backend now exposes `POST /api/alerts` and `GET /api/alerts/mine`; React dashboard includes a wait-time alert subscription panel for the selected counter.
- Analytics/reporting work has started.
- Backend now exposes manager analytics routes under `/api/analytics` for summary, hourly trends, and CSV export; React dashboard includes a queue history report panel with window selection and CSV download.
- ML service work has started.
- Flask now exposes `POST /ml/predict` with deterministic feature-based wait prediction, best-slot output, health check, and compatibility `GET /ml/crowd-count`; Spring Boot now calls the ML service and falls back to its internal baseline if the service is down.
- Deployment work has started.
- Added Dockerfiles for Spring Boot, React/nginx, and Flask ML service; added `docker-compose.yml`, `.env.example`, nginx SPA config, and root deployment README for a full-stack demo run.
- Deployment readiness improved with backend health endpoint, Compose backend healthcheck, Docker-safe Flask binding, root `.gitignore`, and verification commands in the README.
- Local verification is still pending because this environment does not have `mvn`, `java`, or `javac` on PATH.
- Frontend build verification is pending because `npm install` ran out of disk space (`ENOSPC`) while creating `node_modules`; the partial folder was removed.

## First Implementation To Do

Build the Spring Boot backend foundation with PostgreSQL persistence.

This should come before YOLO, dashboard polish, reports, or deployment because the full system depends on a real backend data model. Once locations, counters, users, queue snapshots, and predictions are stored correctly, the ML service and frontend can connect to stable APIs.

### First Backend Scope

1. Clean Spring Boot package structure under `com.crowdmanagement`.
2. Add Spring Data JPA and PostgreSQL dependencies.
3. Create entities:
   - `User`
   - `Location`
   - `Counter`
   - `QueueSnapshot`
   - `Prediction`
   - `AlertSubscription`
4. Create repositories for each entity.
5. Add DTOs and validation for API requests/responses.
6. Implement REST APIs:
   - `POST /api/auth/register`
   - `POST /api/auth/login`
   - `GET /api/locations`
   - `POST /api/locations`
   - `GET /api/counters?locationId=...`
   - `POST /api/counters`
   - `PATCH /api/counters/{id}`
   - `POST /api/queue`
   - `GET /api/queue/latest?counterId=...`
   - `GET /api/queue/history?counterId=...`
   - `GET /api/predict/now?counterId=...`
7. Replace demo security with JWT + roles:
   - `USER`
   - `ADMIN`
   - `MANAGER`
8. Add seed data for one location and a few counters.
9. Add backend tests for APIs and service logic.

## Full Completion Order

### Phase 1 - Backend Core

- PostgreSQL schema and JPA entities
- REST APIs for auth, locations, counters, snapshots, and predictions
- Baseline wait-time calculation if ML is unavailable
- Role-based security

### Phase 2 - Real-Time Updates

- Add Spring WebSocket/STOMP endpoint `/ws`
- Publish live counter updates to `/topic/counter.{id}.live`
- Frontend subscribes instead of only polling
- Optional Redis can be added after local WebSocket flow works

### Phase 3 - Frontend User/Admin Dashboard

- User view:
  - Live queue status
  - Predicted wait time
  - Best visiting slots
  - Alert subscription
- Admin view:
  - Add/manage locations and counters
  - Open/close/pause counters
  - Submit manual queue updates
  - View live charts and alerts
- Manager view:
  - Trends
  - Peak hours
  - CSV/PDF reports

### Phase 4 - ML Prediction Service

- Replace random count endpoint with prediction endpoint.
- Build features from queue snapshots:
  - current queue length
  - service rate
  - time of day
  - day of week
  - recent arrival rate
- Start with baseline formula.
- Then train `RandomForestRegressor` or another scikit-learn model.
- Return:
  - predicted wait time
  - confidence or model version
  - best visiting slots

### Phase 5 - Camera/Computer Vision

- Add YOLOv8/OpenCV person detection.
- Count people inside a Region of Interest.
- Send counts to `POST /api/queue` with `source=CAMERA`.
- Do not store raw images by default.
- Generate alerts when crowd exceeds safe capacity.

### Phase 6 - Reports, Alerts, And Analytics

- Alert subscriptions by wait-time threshold.
- Backend notification trigger.
- Daily/weekly analytics APIs.
- CSV export.
- PDF report generation.
- Peak-hour heatmap data.

### Phase 7 - Deployment And Final Submission

- Docker Compose services:
  - Spring Boot backend
  - React frontend
  - Python ML service
  - PostgreSQL
  - optional Redis
- Environment-based config.
- Final README with setup/run commands.
- Final project report and presentation.
- Test evidence and screenshots/demo video.

## Definition Of Fully Complete

The project is complete when a user can:

1. Register/login.
2. View real locations and counters.
3. See live queue/crowd status.
4. See predicted wait time and best visiting slots.
5. Subscribe to alerts.

An admin can:

1. Manage counters and locations.
2. Update queue counts manually or receive camera counts.
3. See live dashboard updates.
4. Receive overcrowding/long-wait alerts.

A manager can:

1. View analytics.
2. Export reports.
3. Review historical queue trends.

The system must also:

1. Store history in PostgreSQL.
2. Use JWT role-based security.
3. Include a working Python ML prediction service.
4. Include Docker-based local deployment.
5. Include tests and final documentation.

---

## Update: Gap-closing pass (this session)

All five previously-missing items were implemented. Status of each, and how
each was verified:

| Item | Status | Verified how |
|---|---|---|
| Real ML model (scikit-learn) | ✅ Done | `ml-service/train_model.py` trains a `RandomForestRegressor` on realistic synthetic queueing data (MAE ≈ 6 min, R² ≈ 0.97) and saves `model.pkl`. `app.py`'s `/ml/predict` now calls it, with automatic fallback to the old baseline formula if `model.pkl` is missing. **Ran locally and confirmed working** (health check reports `model_loaded: true`, predictions verified sane). |
| Camera / OpenCV crowd counting | ✅ Done | New `/ml/detect-crowd` endpoint in `app.py` uses OpenCV's built-in HOG+SVM people detector (no extra model download required - see `requirements-cv.txt` for an optional YOLOv8 upgrade path). New backend `POST /api/camera/count` (`CameraController` + `CameraCountService`) forwards an uploaded frame to that endpoint and records the result as a `CAMERA`-sourced queue snapshot, reusing the existing live-dashboard pipeline. **ML endpoint ran and tested locally**; backend controller/service code is new but **not build-verified** (see note below).
| Automated backend tests | ✅ Added | New test classes: `AnalyticsServiceTest`, `PdfReportServiceTest` (unit/Mockito), `AuthFlowIntegrationTest`, `QueueFlowIntegrationTest` (Spring Boot + H2, `application-test.properties` profile). **Not run** (see note below) - run with `mvn test` to confirm. |
| PDF report generation | ✅ Done | New `PdfReportService` (Apache PDFBox) + `GET /api/analytics/export.pdf`, alongside the existing CSV export. Frontend has an "Export PDF" button next to "Export CSV" in the analytics panel. **Not build-verified** (see note below). |
| Redis caching | ✅ Done | New `CacheConfig` (`spring-boot-starter-data-redis` + `spring-boot-starter-cache`), `@Cacheable`/`@CacheEvict` on `LocationService`'s location/counter lookups, `redis` service added to `docker-compose.yml`. Falls back to in-memory caching via `CACHE_TYPE=simple` for local dev without Docker. **Not build-verified** (see note below). |

### ⚠️ Important: Java backend changes are not build-verified

The sandbox this update was written in has **no access to Maven Central**
(only a small allow-list of package registries), and no `mvn` binary
installed, so the new/changed Java files could not be compiled or
test-run here. They were written carefully against the existing code's
patterns and conventions, but **you should run the following locally
before trusting them**:

```bash
cd backend-springboot
mvn clean test           # runs the new + existing test suite
mvn spring-boot:run       # or: docker compose up --build
```

If you hit a compile error, paste it back and it can be fixed quickly - it's
much faster to fix a specific compiler error than to guess blindly.

The Python `ml-service` changes, by contrast, **were** run and verified in
this environment (both `/ml/predict` and `/ml/detect-crowd` were smoke-tested
against the running Flask app).

### New files/endpoints summary
- `ml-service/train_model.py` (new) - run to (re)train the wait-time model
- `ml-service/model.pkl`, `ml-service/model_meta.json` (new, generated)
- `POST /ml/detect-crowd` (new, ml-service)
- `POST /api/camera/count` (new, backend) - multipart image upload, ADMIN/MANAGER only
- `GET /api/analytics/export.pdf` (new, backend) - ADMIN/MANAGER only
- `backend-springboot/.../config/CacheConfig.java` (new)
- `backend-springboot/.../service/{PdfReportService,CameraCountService}.java` (new)
- `backend-springboot/.../controller/CameraController.java` (new)
- 4 new test classes under `src/test/java`
- `docker-compose.yml`: added `redis` service
- `.env.example`: documented `REDIS_HOST`, `REDIS_PORT`, `CACHE_TYPE`
