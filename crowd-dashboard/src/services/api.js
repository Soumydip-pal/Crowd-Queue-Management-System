const API_BASE = process.env.REACT_APP_API_BASE || "http://localhost:8080/api";

async function request(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: {
      "Content-Type": "application/json",
      ...options.headers,
    },
    ...options,
  });

  if (!response.ok) {
    let message = "Backend not reachable";
    try {
      const body = await response.json();
      message = body.error || message;
    } catch (err) {
      message = response.statusText || message;
    }
    throw new Error(message);
  }

  return response.json();
}

export async function login(email, password) {
  return request("/auth/login", {
    method: "POST",
    body: JSON.stringify({ email, password }),
  });
}

export async function getLocations() {
  return request("/locations");
}

export async function createLocation({ token, name, address }) {
  return request("/locations", {
    method: "POST",
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: JSON.stringify({ name, address }),
  });
}

export async function getCounters(locationId) {
  const suffix = locationId ? `?locationId=${locationId}` : "";
  return request(`/counters${suffix}`);
}

export async function createCounter({ token, locationId, name, status, serviceRatePerHour }) {
  return request("/counters", {
    method: "POST",
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: JSON.stringify({ locationId, name, status, serviceRatePerHour }),
  });
}

export async function updateCounter({ token, counterId, status, serviceRatePerHour }) {
  return request(`/counters/${counterId}`, {
    method: "PATCH",
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: JSON.stringify({ status, serviceRatePerHour }),
  });
}

export async function getLiveStatus(counterId = 1) {
  return request(`/queue/live?counterId=${counterId}`);
}

export async function postQueueUpdate({ token, counterId, currentLength, source = "MANUAL" }) {
  return request("/queue", {
    method: "POST",
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: JSON.stringify({ counterId, currentLength, source }),
  });
}

export async function createAlertSubscription({
  token,
  counterId,
  thresholdWaitMin,
  notifyChannel = "EMAIL",
}) {
  return request("/alerts", {
    method: "POST",
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: JSON.stringify({ counterId, thresholdWaitMin, notifyChannel }),
  });
}

export async function getMyAlertSubscriptions(token) {
  return request("/alerts/mine", {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
}

export async function getAnalyticsSummary({ token, counterId, hours = 24 }) {
  return request(`/analytics/summary?counterId=${counterId}&hours=${hours}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
}

export async function getHourlyAnalytics({ token, counterId, hours = 24 }) {
  return request(`/analytics/hourly?counterId=${counterId}&hours=${hours}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
}

export async function getAnalyticsCsv({ token, counterId, hours = 24 }) {
  const response = await fetch(`${API_BASE}/analytics/export.csv?counterId=${counterId}&hours=${hours}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });

  if (!response.ok) {
    throw new Error(response.statusText || "CSV export failed");
  }

  return response.text();
}

export async function downloadAnalyticsPdf({ token, counterId, hours = 24 }) {
  const response = await fetch(`${API_BASE}/analytics/export.pdf?counterId=${counterId}&hours=${hours}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });

  if (!response.ok) {
    throw new Error(response.statusText || "PDF export failed");
  }

  const blob = await response.blob();
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = "queue-report.pdf";
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(url);
}

export async function uploadCameraFrame({ token, counterId, imageFile, roi }) {
  const formData = new FormData();
  formData.append("image", imageFile);
  if (roi) {
    formData.append("roi", JSON.stringify(roi));
  }

  const response = await fetch(`${API_BASE}/camera/count?counterId=${counterId}`, {
    method: "POST",
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: formData,
  });

  if (!response.ok) {
    let message = "Camera count failed";
    try {
      const body = await response.json();
      message = body.error || message;
    } catch (err) {
      message = response.statusText || message;
    }
    throw new Error(message);
  }

  return response.json();
}

export async function getCrowdStatus() {
  return request("/crowd-status");
}
