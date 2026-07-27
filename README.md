# Real-Time Crowd & Queue Management System

Full-stack crowd and queue management demo using Spring Boot, React, PostgreSQL, and a Python ML service.

## Services

- `backend-springboot`: Java 17 Spring Boot API, auth, queue snapshots, WebSocket updates, analytics, alerts, ML integration
- `crowd-dashboard`: React dashboard for live monitoring, admin controls, alerts, analytics, and CSV export
- `ml-service`: Flask prediction service with deterministic baseline wait-time prediction
- `postgres`: PostgreSQL database for users, locations, counters, queue history, predictions, alerts

## Docker Run

1. Copy the environment template:

```bash
copy .env.example .env
```

2. Start the full stack:

```bash
docker compose up --build
```

3. Open the dashboard:

```text
http://localhost:3000
```

4. Login with the seeded admin account:

```text
admin@example.com
admin123
```

## Useful URLs

- Frontend: `http://localhost:3000`
- Backend API: `http://localhost:8080/api`
- Backend health: `http://localhost:8080/api/health`
- Backend WebSocket endpoint: `http://localhost:8080/ws`
- ML health: `http://localhost:5001/health`
- PostgreSQL: `localhost:5432`

## Demo Flow

1. Login as admin.
2. Create or select a location and counter.
3. Submit a manual queue count.
4. Watch the live dashboard update through WebSocket or REST fallback.
5. Create a wait-time alert subscription.
6. Review analytics and export CSV history.

## Local Development

Backend:

```bash
cd backend-springboot
mvn spring-boot:run
```

ML service:

```bash
cd ml-service
python app.py
```

Frontend:

```bash
cd crowd-dashboard
npm install
npm start
```

## Deployment Notes

- Change `JWT_SECRET` before production use.
- Use a managed PostgreSQL instance for cloud deployment.
- Set `CORS_ALLOWED_ORIGINS` to the deployed frontend URL.
- Set `REACT_APP_API_BASE` and `REACT_APP_WS_URL` to public backend URLs before building the frontend image.
- The current ML model is a deterministic baseline. A trained scikit-learn model can replace it while keeping the `/ml/predict` API contract.

## Verification Commands

Use these once Docker, Java, Maven, Node, and Python are available locally:

```bash
docker compose config
docker compose up --build
```

In separate terminals:

```bash
cd backend-springboot
mvn test
```

```bash
cd crowd-dashboard
npm install
npm run build
```

```bash
cd ml-service
python -m py_compile app.py
```

## Run in VS Code

1. Open the folder: **File → Open Folder…** → select `Crowd Management System`.
2. Recommended extensions (VS Code will prompt you, or install manually):
   - **Extension Pack for Java** (Microsoft) - for `backend-springboot`
   - **Python** (Microsoft) - for `ml-service`
   - **ES7+ React/Redux snippets** or just the built-in JS/TS support - for `crowd-dashboard`
   - **Docker** (Microsoft) - if you'll use `docker compose up`
3. Use VS Code's integrated terminal (`` Ctrl+` ``/`` Cmd+` ``) - open one terminal
   tab per service, since all three run simultaneously:

   **Terminal 1 - backend:**
   ```bash
   cd backend-springboot
   mvn spring-boot:run
   ```
   (Or use the Java extension's "Run" CodeLens above `CrowdApplication.java`'s `main` method.)

   **Terminal 2 - ML service:**
   ```bash
   cd ml-service
   pip install -r requirements.txt --break-system-packages
   python train_model.py    # one-time: trains model.pkl
   python app.py
   ```

   **Terminal 3 - frontend:**
   ```bash
   cd crowd-dashboard
   npm install
   npm start
   ```
4. Open `http://localhost:3000` in your browser. Login: `admin@example.com` / `admin123`.
5. Alternatively, skip terminals 1-2 and just run `docker compose up --build`
   from the repo root (needs Docker Desktop) - then only run terminal 3 for
   the frontend, or also let Docker build it (see docker-compose.yml).

## Push to GitHub

1. Create a new empty repo on GitHub (no README/gitignore - this project
   already has them): https://github.com/new
2. In VS Code's terminal, from the project root:
   ```bash
   git init
   git add .
   git commit -m "Initial commit"
   git branch -M main
   git remote add origin https://github.com/<your-username>/<your-repo>.git
   git push -u origin main
   ```
3. If `git` asks for credentials, use a GitHub Personal Access Token as the
   password (GitHub → Settings → Developer settings → Personal access
   tokens), or sign in via VS Code's built-in GitHub authentication
   (**Source Control** panel → **Publish to GitHub** button does steps 1-3
   for you automatically, if you'd rather skip the terminal).
4. Double-check `.env` was NOT pushed (it's in `.gitignore` already) - only
   `.env.example` should be in the repo.



This repo includes a `render.yaml` Blueprint that provisions Postgres, Redis
(Key Value), the ML service, and the backend API in one step. The React
frontend deploys separately as a free Static Site (simpler than Docker for a
static React build - no port wiring needed).

### 1. Push to GitHub first
See "Push to GitHub" below if you haven't already - Render deploys from a
Git repo, not a local folder or zip.

### 2. Deploy the backend + ML service + databases (Blueprint)
1. Go to https://dashboard.render.com → **New** → **Blueprint**.
2. Connect your GitHub account and select this repository.
3. Render detects `render.yaml` and shows a preview of 4 resources:
   `crowd-postgres` (database), `crowd-redis` (Key Value), `crowd-ml-service`,
   `crowd-backend`. Click **Apply**.
4. Wait for all services to show **Live** (the ML service builds a Docker
   image and trains the model during build, so its first deploy takes a few
   extra minutes - that's expected).
5. Copy `crowd-backend`'s public URL, e.g. `https://crowd-backend-xxxx.onrender.com`.

### 3. Deploy the frontend (Static Site)
1. **New** → **Static Site** → select this repo again.
2. Root directory: `crowd-dashboard`
3. Build command: `npm install && npm run build`
4. Publish directory: `build`
5. Add environment variables:
   - `REACT_APP_API_BASE` = `https://<your-backend-url>/api`
   - `REACT_APP_WS_URL` = `https://<your-backend-url>/ws`
6. Click **Create Static Site**. Once live, copy its URL, e.g.
   `https://crowd-dashboard.onrender.com`.

### 4. Close the loop: allow the frontend through CORS
1. Go to the `crowd-backend` service → **Environment**.
2. Edit `CORS_ALLOWED_ORIGINS` to your Static Site's URL from step 3
   (e.g. `https://crowd-dashboard.onrender.com`), save. This triggers a
   redeploy.

### Notes
- Free-tier web services on Render spin down after inactivity and take
  ~30-60s to wake on the next request - the first login after idle time may
  time out once and succeed on retry.
- Login with the seeded account: `admin@example.com` / `admin123`.
- To retrain the ML model on real data later, see `ml-service/train_model.py`
  and its `--real-data` flag, then trigger a manual redeploy of
  `crowd-ml-service` (it retrains automatically on every build).

## Camera-based crowd counting - 3 ways to feed it

All three feed the same pipeline (`POST /api/camera/count` → CAMERA-sourced
queue snapshot → live dashboard), so they show up identically no matter
which one you use:

| Method | What it needs | Continuous? |
|---|---|---|
| **Single photo upload** | Any phone/computer, one photo at a time | No - manual, one-shot |
| **Live browser camera** | Any device with a browser + camera (laptop, phone, tablet) | Yes, while the dashboard tab is open (auto-captures every 5-60s) |
| **CCTV / IP camera (RTSP)** | An actual CCTV/IP camera's RTSP URL, run as a background process | Yes, runs independently of any browser tab |

**Live browser camera**: open the dashboard, log in, and in the "Live
camera" section click **Start Live Camera**. Works on a laptop's webcam or
a phone's camera (opens the rear camera by default on phones) - it keeps
counting for as long as that browser tab stays open.

**Real CCTV/IP camera**: see `ml-service/camera_poller.py` - a standalone
script you run once on a server/Raspberry Pi near your cameras. It connects
to your camera's RTSP URL, detects people every N seconds, and pushes counts
to the backend - independent of any browser. Example:

```bash
cd ml-service
export BACKEND_URL=http://localhost:8080/api
export SERVICE_EMAIL=admin@example.com
export SERVICE_PASSWORD=admin123
export CAMERAS='[{"counter_id":1,"rtsp_url":"rtsp://user:pass@192.168.1.50:554/stream1"}]'
python camera_poller.py
```

You can test it without real CCTV hardware first by using your computer's
own webcam - set `"rtsp_url":"0"` (camera index 0) instead of an RTSP URL.

Most consumer/commercial IP cameras (Hikvision, Dahua, TP-Link, Reolink,
etc.) expose an RTSP URL in their app/web settings, typically formatted like
`rtsp://<username>:<password>@<camera-ip>:554/<stream-path>` - check your
camera's manual for the exact path.
