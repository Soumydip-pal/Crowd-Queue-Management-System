# Crowd Dashboard

React dashboard for the Crowd & Queue Management System.

## Local Run

```bash
npm install
npm start
```

Default URLs:

- API: `http://localhost:8080/api`
- WebSocket: `http://localhost:8080/ws`

Override them with:

```bash
REACT_APP_API_BASE=http://localhost:8080/api
REACT_APP_WS_URL=http://localhost:8080/ws
```

## Docker

The Docker build accepts:

- `REACT_APP_API_BASE`
- `REACT_APP_WS_URL`

These are baked into the production React build.
