import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { useEffect, useMemo, useRef, useState } from "react";
import {
  FiActivity,
  FiAlertTriangle,
  FiBarChart2,
  FiBell,
  FiCamera,
  FiCheckCircle,
  FiClock,
  FiDownload,
  FiLogOut,
  FiPlusCircle,
  FiSave,
  FiSend,
  FiUsers,
} from "react-icons/fi";
import {
  createCounter,
  createAlertSubscription,
  createLocation,
  downloadAnalyticsPdf,
  getAnalyticsCsv,
  getAnalyticsSummary,
  getCounters,
  getHourlyAnalytics,
  getLiveStatus,
  getLocations,
  getMyAlertSubscriptions,
  login,
  postQueueUpdate,
  updateCounter,
  uploadCameraFrame,
} from "../services/api";
import Topbar from "../components/Topbar";
import Sidebar from "../components/Sidebar";
import MetricCard from "../components/MetricCard";
import LiveCameraWidget from "../components/LiveCameraWidget";
import LiveChart from "../components/LiveChart";
import HourlyChart from "../components/HourlyChart";

const WS_URL = process.env.REACT_APP_WS_URL || "http://localhost:8080/ws";

export default function Dashboard() {
  const [locations, setLocations] = useState([]);
  const [counters, setCounters] = useState([]);
  const [selectedCounterId, setSelectedCounterId] = useState(1);
  const [live, setLive] = useState(null);
  const [history, setHistory] = useState([]);
  const [connectionState, setConnectionState] = useState("connecting");
  const [error, setError] = useState(null);
  const [adminSession, setAdminSession] = useState(loadSavedSession);
  const [loginForm, setLoginForm] = useState({
    email: "admin@example.com",
    password: "admin123",
  });
  const [manualCount, setManualCount] = useState("");
  const [locationForm, setLocationForm] = useState({ name: "", address: "" });
  const [counterForm, setCounterForm] = useState({
    locationId: "",
    name: "",
    status: "OPEN",
    serviceRatePerHour: 30,
  });
  const [counterEditForm, setCounterEditForm] = useState({
    status: "OPEN",
    serviceRatePerHour: 30,
  });
  const [alertForm, setAlertForm] = useState({
    thresholdWaitMin: 15,
    notifyChannel: "EMAIL",
  });
  const [alertSubscriptions, setAlertSubscriptions] = useState([]);
  const [analyticsHours, setAnalyticsHours] = useState(24);
  const [analyticsSummary, setAnalyticsSummary] = useState(null);
  const [hourlyAnalytics, setHourlyAnalytics] = useState([]);
  const [adminMessage, setAdminMessage] = useState("");
  const [alertMessage, setAlertMessage] = useState("");
  const [analyticsMessage, setAnalyticsMessage] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const connectionStateRef = useRef("connecting");

  const selectedCounter = useMemo(
    () => counters.find((counter) => counter.id === Number(selectedCounterId)),
    [counters, selectedCounterId]
  );

  useEffect(() => {
    loadReferenceData();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (!selectedCounter) {
      return;
    }
    setCounterEditForm({
      status: selectedCounter.status,
      serviceRatePerHour: selectedCounter.serviceRatePerHour,
    });
  }, [selectedCounter]);

  useEffect(() => {
    if (!adminSession?.accessToken) {
      setAlertSubscriptions([]);
      setAnalyticsSummary(null);
      setHourlyAnalytics([]);
      return;
    }
    loadAlertSubscriptions(adminSession.accessToken);
    loadAnalytics(adminSession.accessToken, selectedCounterId, analyticsHours);
  }, [adminSession, selectedCounterId, analyticsHours]);

  async function loadReferenceData(preferredCounterId) {
    try {
      const [locationList, counterList] = await Promise.all([
        getLocations(),
        getCounters(),
      ]);
      setLocations(locationList);
      setCounters(counterList);

      if (locationList.length > 0) {
        setCounterForm((current) => ({
          ...current,
          locationId: current.locationId || locationList[0].id,
        }));
      }

      if (counterList.length > 0) {
        const nextCounterId = preferredCounterId || selectedCounterId || counterList[0].id;
        const exists = counterList.some((counter) => counter.id === Number(nextCounterId));
        setSelectedCounterId(exists ? Number(nextCounterId) : counterList[0].id);
      }
    } catch (err) {
      setError(err.message);
    }
  }

  async function loadAlertSubscriptions(token) {
    try {
      const subscriptions = await getMyAlertSubscriptions(token);
      setAlertSubscriptions(subscriptions);
    } catch (err) {
      setAlertMessage(err.message);
    }
  }

  async function loadAnalytics(token, counterId, hours) {
    if (!token || !counterId) {
      return;
    }
    try {
      const [summary, hourly] = await Promise.all([
        getAnalyticsSummary({ token, counterId, hours }),
        getHourlyAnalytics({ token, counterId, hours }),
      ]);
      setAnalyticsSummary(summary);
      setHourlyAnalytics(hourly);
      setAnalyticsMessage("");
    } catch (err) {
      setAnalyticsMessage(err.message);
    }
  }

  useEffect(() => {
    if (!selectedCounterId) {
      return undefined;
    }

    let pollTimer;
    connectionStateRef.current = "connecting";
    setConnectionState("connecting");

    async function loadInitialStatus() {
      try {
        const data = await getLiveStatus(selectedCounterId);
        applyLiveUpdate(data);
      } catch (err) {
        setError(err.message);
      }
    }

    loadInitialStatus();

    const client = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      reconnectDelay: 5000,
      onConnect: () => {
        connectionStateRef.current = "live";
        setConnectionState("live");
        client.subscribe(`/topic/counter.${selectedCounterId}.live`, (message) => {
          applyLiveUpdate(JSON.parse(message.body));
        });
      },
      onStompError: () => markPolling(),
      onWebSocketError: () => markPolling(),
      onWebSocketClose: () => markPolling(),
    });

    client.activate();

    pollTimer = setInterval(async () => {
      if (connectionStateRef.current === "live") {
        return;
      }
      try {
        const data = await getLiveStatus(selectedCounterId);
        applyLiveUpdate(data);
      } catch (err) {
        setError(err.message);
      }
    }, 5000);

    return () => {
      clearInterval(pollTimer);
      client.deactivate();
    };
  }, [selectedCounterId]);

  function markPolling() {
    connectionStateRef.current = "polling";
    setConnectionState("polling");
  }

  function applyLiveUpdate(data) {
    setLive(data);
    setError(null);
    setHistory((previous) => [
      ...previous.slice(-11),
      {
        time: new Date(data.timestamp).toLocaleTimeString(),
        count: data.currentLength,
        wait: data.predictedWaitMin,
      },
    ]);
  }

  async function handleLogin(event) {
    event.preventDefault();
    setIsSubmitting(true);
    setAdminMessage("");
    try {
      const session = await login(loginForm.email, loginForm.password);
      setAdminSession(session);
      window.localStorage.setItem("crowd_admin_session", JSON.stringify(session));
      await loadAlertSubscriptions(session.accessToken);
      setAdminMessage(`Signed in as ${session.role}`);
    } catch (err) {
      setAdminMessage(err.message);
    } finally {
      setIsSubmitting(false);
    }
  }

  function handleLogout() {
    setAdminSession(null);
    setAlertSubscriptions([]);
    window.localStorage.removeItem("crowd_admin_session");
    setAdminMessage("Signed out");
  }

  async function handleManualUpdate(event) {
    event.preventDefault();
    if (!adminSession) {
      setAdminMessage("Admin login required");
      return;
    }

    const parsedCount = Number(manualCount);
    if (!Number.isInteger(parsedCount) || parsedCount < 0) {
      setAdminMessage("Enter a whole number greater than or equal to 0");
      return;
    }

    setIsSubmitting(true);
    setAdminMessage("");
    try {
      const payload = await postQueueUpdate({
        token: adminSession.accessToken,
        counterId: selectedCounterId,
        currentLength: parsedCount,
        source: "MANUAL",
      });
      applyLiveUpdate(payload);
      await loadAnalytics(adminSession.accessToken, selectedCounterId, analyticsHours);
      setManualCount("");
      setAdminMessage("Manual queue update submitted");
    } catch (err) {
      setAdminMessage(err.message);
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleCreateLocation(event) {
    event.preventDefault();
    if (!adminSession) {
      setAdminMessage("Admin login required");
      return;
    }

    setIsSubmitting(true);
    setAdminMessage("");
    try {
      const location = await createLocation({
        token: adminSession.accessToken,
        name: locationForm.name,
        address: locationForm.address,
      });
      setLocationForm({ name: "", address: "" });
      setCounterForm((current) => ({ ...current, locationId: location.id }));
      await loadReferenceData(selectedCounterId);
      setAdminMessage("Location created");
    } catch (err) {
      setAdminMessage(err.message);
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleCreateCounter(event) {
    event.preventDefault();
    if (!adminSession) {
      setAdminMessage("Admin login required");
      return;
    }

    const serviceRate = Number(counterForm.serviceRatePerHour);
    if (!Number.isInteger(serviceRate) || serviceRate < 1) {
      setAdminMessage("Service rate must be a whole number greater than 0");
      return;
    }

    setIsSubmitting(true);
    setAdminMessage("");
    try {
      const counter = await createCounter({
        token: adminSession.accessToken,
        locationId: Number(counterForm.locationId),
        name: counterForm.name,
        status: counterForm.status,
        serviceRatePerHour: serviceRate,
      });
      setCounterForm((current) => ({
        ...current,
        name: "",
        serviceRatePerHour: 30,
      }));
      setHistory([]);
      await loadReferenceData(counter.id);
      setAdminMessage("Counter created");
    } catch (err) {
      setAdminMessage(err.message);
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleUpdateCounter(event) {
    event.preventDefault();
    if (!adminSession) {
      setAdminMessage("Admin login required");
      return;
    }

    const serviceRate = Number(counterEditForm.serviceRatePerHour);
    if (!Number.isInteger(serviceRate) || serviceRate < 1) {
      setAdminMessage("Service rate must be a whole number greater than 0");
      return;
    }

    setIsSubmitting(true);
    setAdminMessage("");
    try {
      const counter = await updateCounter({
        token: adminSession.accessToken,
        counterId: selectedCounterId,
        status: counterEditForm.status,
        serviceRatePerHour: serviceRate,
      });
      await loadReferenceData(counter.id);
      setAdminMessage("Counter settings updated");
    } catch (err) {
      setAdminMessage(err.message);
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleCreateAlert(event) {
    event.preventDefault();
    if (!adminSession) {
      setAlertMessage("Login required to create alerts");
      return;
    }

    const threshold = Number(alertForm.thresholdWaitMin);
    if (!Number.isInteger(threshold) || threshold < 1) {
      setAlertMessage("Alert threshold must be a whole number greater than 0");
      return;
    }

    setIsSubmitting(true);
    setAlertMessage("");
    try {
      await createAlertSubscription({
        token: adminSession.accessToken,
        counterId: selectedCounterId,
        thresholdWaitMin: threshold,
        notifyChannel: alertForm.notifyChannel,
      });
      await loadAlertSubscriptions(adminSession.accessToken);
      setAlertMessage("Alert subscription saved");
    } catch (err) {
      setAlertMessage(err.message);
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleExportCsv() {
    if (!adminSession) {
      setAnalyticsMessage("Login required to export reports");
      return;
    }

    setAnalyticsMessage("");
    try {
      const csv = await getAnalyticsCsv({
        token: adminSession.accessToken,
        counterId: selectedCounterId,
        hours: analyticsHours,
      });
      const blob = new Blob([csv], { type: "text/csv" });
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = `queue-history-counter-${selectedCounterId}.csv`;
      link.click();
      window.URL.revokeObjectURL(url);
      setAnalyticsMessage("CSV report generated");
    } catch (err) {
      setAnalyticsMessage(err.message);
    }
  }

  async function handleExportPdf() {
    if (!adminSession) {
      setAnalyticsMessage("Login required to export reports");
      return;
    }

    setAnalyticsMessage("");
    try {
      await downloadAnalyticsPdf({
        token: adminSession.accessToken,
        counterId: selectedCounterId,
        hours: analyticsHours,
      });
      setAnalyticsMessage("PDF report generated");
    } catch (err) {
      setAnalyticsMessage(err.message);
    }
  }

  async function handleCameraUpload(event) {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) {
      return;
    }
    if (!adminSession) {
      setAdminMessage("Login required to submit camera counts");
      return;
    }

    setIsSubmitting(true);
    setAdminMessage("");
    try {
      const result = await uploadCameraFrame({
        token: adminSession.accessToken,
        counterId: selectedCounterId,
        imageFile: file,
      });
      setAdminMessage(`Camera detected ${result.currentLength} people - queue updated`);
    } catch (err) {
      setAdminMessage(err.message);
    } finally {
      setIsSubmitting(false);
    }
  }

  const isCrowded = live?.status === "Overcrowded";

  return (
    <div className="app-shell">
      <Topbar
        connectionState={connectionState}
        onMenuClick={() => setSidebarOpen((open) => !open)}
        adminSession={adminSession}
      />

      <div className="app-body">
        <Sidebar
          open={sidebarOpen}
          onClose={() => setSidebarOpen(false)}
          counters={counters}
          selectedCounterId={selectedCounterId}
          onSelectCounter={(id) => {
            setHistory([]);
            setSelectedCounterId(id);
          }}
          locationsCount={locations.length}
        />

        <main className="dashboard-shell">
          <section id="overview" className="page-intro">
            <div>
              <p className="eyebrow">Real-time crowd &amp; queue management</p>
              <h1>Live Queue Control</h1>
              <p className="page-subtitle">
                Monitor live crowd levels, manage counters, and keep visitors informed with
                predicted wait times — all from one colorful control center.
              </p>
            </div>
            <div className="page-intro-select">
              <label>
                Counter
                <select
                  value={selectedCounterId}
                  onChange={(event) => {
                    setHistory([]);
                    setSelectedCounterId(Number(event.target.value));
                  }}
                >
                  {counters.map((counter) => (
                    <option key={counter.id} value={counter.id}>
                      {counter.locationName} - {counter.name}
                    </option>
                  ))}
                </select>
              </label>
            </div>
          </section>

          {error && (
            <div className="alert-banner danger">
              <FiAlertTriangle />
              <span>{error}</span>
            </div>
          )}

          <section className="metrics-grid">
            <MetricCard
              label="Current crowd"
              value={live?.currentLength ?? 0}
              icon={FiUsers}
              accent="violet"
            />
            <MetricCard
              label="Predicted wait"
              value={`${live?.predictedWaitMin ?? 0} min`}
              icon={FiClock}
              accent="amber"
            />
            <MetricCard
              label="Counter status"
              value={selectedCounter?.status ?? "OPEN"}
              icon={FiActivity}
              accent="teal"
            />
            <MetricCard
              label="Input source"
              value={live?.source ?? "API"}
              icon={FiSend}
              accent="pink"
            />
          </section>

          <section className={`live-band ${isCrowded ? "danger-band" : "safe-band"}`}>
            <div className="live-band-copy">
              {isCrowded ? <FiAlertTriangle /> : <FiCheckCircle />}
              <div>
                <span className="muted">Safety status</span>
                <h2>{live?.status ?? "Loading"}</h2>
                <p>
                  {isCrowded
                    ? "Crowd level or predicted wait crossed the alert threshold."
                    : "Crowd level is within the configured baseline threshold."}
                </p>
              </div>
            </div>
            <LiveChart data={history} />
          </section>

          <section id="admin-console" className="card admin-panel">
            <div className="admin-copy">
              <span className="muted">Admin console</span>
              <h2>Manual Queue Update</h2>
              <p>
                Operators can post live queue counts for the selected counter. The backend stores
                the snapshot, recalculates baseline prediction, and broadcasts the update.
              </p>
            </div>

            {!adminSession ? (
              <form className="admin-form" onSubmit={handleLogin}>
                <label>
                  Email
                  <input
                    type="email"
                    value={loginForm.email}
                    onChange={(event) =>
                      setLoginForm((current) => ({ ...current, email: event.target.value }))
                    }
                  />
                </label>
                <label>
                  Password
                  <input
                    type="password"
                    value={loginForm.password}
                    onChange={(event) =>
                      setLoginForm((current) => ({ ...current, password: event.target.value }))
                    }
                  />
                </label>
                <button type="submit" className="btn btn-primary" disabled={isSubmitting}>
                  Sign In
                </button>
              </form>
            ) : (
              <form className="admin-form" onSubmit={handleManualUpdate}>
                <div className="session-row">
                  <span>{adminSession.email}</span>
                  <button type="button" className="btn btn-ghost" onClick={handleLogout}>
                    <FiLogOut /> Sign Out
                  </button>
                </div>
                <label>
                  Current queue length
                  <input
                    type="number"
                    min="0"
                    step="1"
                    value={manualCount}
                    onChange={(event) => setManualCount(event.target.value)}
                    placeholder="Example: 42"
                  />
                </label>
                <button type="submit" className="btn btn-primary" disabled={isSubmitting}>
                  <FiSend /> Submit Count
                </button>
                <label className="btn btn-secondary camera-upload-label">
                  <FiCamera /> Count from camera photo
                  <input
                    type="file"
                    accept="image/*"
                    capture="environment"
                    onChange={handleCameraUpload}
                    disabled={isSubmitting}
                    style={{ display: "none" }}
                  />
                </label>
              </form>
            )}

            {adminSession && (
              <div className="live-camera-section">
                <span className="muted">Live camera (any device with a browser)</span>
                <LiveCameraWidget
                  token={adminSession.accessToken}
                  counterId={selectedCounterId}
                  onResult={(result) =>
                    setAdminMessage(`Camera detected ${result.currentLength} people - queue updated`)
                  }
                />
              </div>
            )}

            {adminMessage && <p className="admin-message">{adminMessage}</p>}
          </section>

          {adminSession && (
            <section className="management-grid">
              <form className="card management-card" onSubmit={handleCreateLocation}>
                <div>
                  <span className="muted">Locations</span>
                  <h2>Create Location</h2>
                </div>
                <label>
                  Name
                  <input
                    required
                    value={locationForm.name}
                    onChange={(event) =>
                      setLocationForm((current) => ({ ...current, name: event.target.value }))
                    }
                    placeholder="Hospital OPD"
                  />
                </label>
                <label>
                  Address
                  <input
                    required
                    value={locationForm.address}
                    onChange={(event) =>
                      setLocationForm((current) => ({ ...current, address: event.target.value }))
                    }
                    placeholder="Block A, Main Gate"
                  />
                </label>
                <button type="submit" className="btn btn-primary" disabled={isSubmitting}>
                  <FiPlusCircle /> Add Location
                </button>
              </form>

              <form className="card management-card" onSubmit={handleCreateCounter}>
                <div>
                  <span className="muted">Counters</span>
                  <h2>Create Counter</h2>
                </div>
                <label>
                  Location
                  <select
                    value={counterForm.locationId}
                    onChange={(event) =>
                      setCounterForm((current) => ({ ...current, locationId: event.target.value }))
                    }
                  >
                    {locations.map((location) => (
                      <option key={location.id} value={location.id}>
                        {location.name}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Counter name
                  <input
                    required
                    value={counterForm.name}
                    onChange={(event) =>
                      setCounterForm((current) => ({ ...current, name: event.target.value }))
                    }
                    placeholder="Counter C"
                  />
                </label>
                <div className="two-column-fields">
                  <label>
                    Status
                    <select
                      value={counterForm.status}
                      onChange={(event) =>
                        setCounterForm((current) => ({ ...current, status: event.target.value }))
                      }
                    >
                      <option value="OPEN">Open</option>
                      <option value="PAUSED">Paused</option>
                      <option value="CLOSED">Closed</option>
                    </select>
                  </label>
                  <label>
                    Service rate/hr
                    <input
                      type="number"
                      min="1"
                      step="1"
                      value={counterForm.serviceRatePerHour}
                      onChange={(event) =>
                        setCounterForm((current) => ({
                          ...current,
                          serviceRatePerHour: event.target.value,
                        }))
                      }
                    />
                  </label>
                </div>
                <button
                  type="submit"
                  className="btn btn-primary"
                  disabled={isSubmitting || locations.length === 0}
                >
                  <FiPlusCircle /> Add Counter
                </button>
              </form>

              <form className="card management-card" onSubmit={handleUpdateCounter}>
                <div>
                  <span className="muted">Selected counter</span>
                  <h2>Update Settings</h2>
                </div>
                <p className="selected-summary">
                  {selectedCounter
                    ? `${selectedCounter.locationName} - ${selectedCounter.name}`
                    : "No counter selected"}
                </p>
                <div className="two-column-fields">
                  <label>
                    Status
                    <select
                      value={counterEditForm.status}
                      onChange={(event) =>
                        setCounterEditForm((current) => ({ ...current, status: event.target.value }))
                      }
                    >
                      <option value="OPEN">Open</option>
                      <option value="PAUSED">Paused</option>
                      <option value="CLOSED">Closed</option>
                    </select>
                  </label>
                  <label>
                    Service rate/hr
                    <input
                      type="number"
                      min="1"
                      step="1"
                      value={counterEditForm.serviceRatePerHour}
                      onChange={(event) =>
                        setCounterEditForm((current) => ({
                          ...current,
                          serviceRatePerHour: event.target.value,
                        }))
                      }
                    />
                  </label>
                </div>
                <button
                  type="submit"
                  className="btn btn-primary"
                  disabled={isSubmitting || !selectedCounter}
                >
                  <FiSave /> Save Counter
                </button>
              </form>
            </section>
          )}

          <section id="alerts" className="card alert-panel">
            <div className="alert-copy">
              <span className="muted">User alerts</span>
              <h2>
                <FiBell /> Wait-Time Subscription
              </h2>
              <p>
                Subscribe to the selected counter and use the threshold to know when the
                predicted wait is acceptable.
              </p>
            </div>

            <form className="alert-form" onSubmit={handleCreateAlert}>
              <label>
                Threshold wait
                <input
                  type="number"
                  min="1"
                  step="1"
                  value={alertForm.thresholdWaitMin}
                  onChange={(event) =>
                    setAlertForm((current) => ({
                      ...current,
                      thresholdWaitMin: event.target.value,
                    }))
                  }
                />
              </label>
              <label>
                Notify channel
                <select
                  value={alertForm.notifyChannel}
                  onChange={(event) =>
                    setAlertForm((current) => ({ ...current, notifyChannel: event.target.value }))
                  }
                >
                  <option value="EMAIL">Email</option>
                  <option value="WEBPUSH">Web push</option>
                </select>
              </label>
              <button type="submit" className="btn btn-primary" disabled={isSubmitting || !adminSession}>
                <FiSave /> Save Alert
              </button>
            </form>

            <div className="subscription-list">
              {alertSubscriptions.map((subscription) => (
                <div className="subscription-row" key={subscription.id}>
                  <strong>{subscription.counterName}</strong>
                  <span>{subscription.thresholdWaitMin} min or less</span>
                  <span className="badge">{subscription.notifyChannel}</span>
                </div>
              ))}
              {alertSubscriptions.length === 0 && (
                <p className="muted">
                  {adminSession ? "No active alert subscriptions yet." : "Login to manage alerts."}
                </p>
              )}
            </div>

            {alertMessage && <p className="admin-message">{alertMessage}</p>}
          </section>

          {adminSession && (
            <section id="analytics" className="card analytics-panel">
              <div className="analytics-header">
                <div>
                  <span className="muted">Manager analytics</span>
                  <h2>
                    <FiBarChart2 /> Queue History Report
                  </h2>
                </div>
                <div className="analytics-actions">
                  <label>
                    Window
                    <select
                      value={analyticsHours}
                      onChange={(event) => setAnalyticsHours(Number(event.target.value))}
                    >
                      <option value={6}>Last 6 hours</option>
                      <option value={24}>Last 24 hours</option>
                      <option value={168}>Last 7 days</option>
                      <option value={720}>Last 30 days</option>
                    </select>
                  </label>
                  <button type="button" className="btn btn-secondary" onClick={handleExportCsv}>
                    <FiDownload /> Export CSV
                  </button>
                  <button type="button" className="btn btn-secondary" onClick={handleExportPdf}>
                    <FiDownload /> Export PDF
                  </button>
                </div>
              </div>

              <div className="analytics-summary-grid">
                <MetricCard label="Samples" value={analyticsSummary?.snapshotCount ?? 0} accent="violet" />
                <MetricCard
                  label="Average crowd"
                  value={analyticsSummary?.averageCrowdLength ?? 0}
                  accent="teal"
                />
                <MetricCard
                  label="Peak crowd"
                  value={analyticsSummary?.peakCrowdLength ?? 0}
                  accent="pink"
                />
                <MetricCard
                  label="Average wait"
                  value={`${analyticsSummary?.averagePredictedWaitMin ?? 0} min`}
                  accent="amber"
                />
              </div>

              <HourlyChart data={hourlyAnalytics} />

              <div className="hourly-table">
                <div className="hourly-row hourly-heading">
                  <span>Hour</span>
                  <span>Samples</span>
                  <span>Avg crowd</span>
                  <span>Avg wait</span>
                </div>
                {hourlyAnalytics.map((row) => (
                  <div className="hourly-row" key={row.hour}>
                    <span>{String(row.hour).padStart(2, "0")}:00</span>
                    <span>{row.snapshotCount}</span>
                    <span>{row.averageCrowdLength}</span>
                    <span>{row.averagePredictedWaitMin} min</span>
                  </div>
                ))}
                {hourlyAnalytics.length === 0 && (
                  <p className="muted">No queue history available for this window.</p>
                )}
              </div>

              {analyticsSummary?.busiestHour !== null && analyticsSummary?.busiestHour !== undefined && (
                <p className="analytics-note">
                  Busiest hour in this window: {String(analyticsSummary.busiestHour).padStart(2, "0")}:00
                </p>
              )}

              {analyticsMessage && <p className="admin-message">{analyticsMessage}</p>}
            </section>
          )}

          <section id="history" className="card history-panel">
            <h2>
              <FiClock /> Recent Live Samples
            </h2>
            <div className="history-list">
              {history.map((item, index) => (
                <div className="history-row" key={`${item.time}-${index}`}>
                  <span>{item.time}</span>
                  <strong>{item.count} people</strong>
                  <span>{item.wait} min wait</span>
                </div>
              ))}
              {history.length === 0 && <p className="muted">Waiting for queue data...</p>}
            </div>
          </section>

          <footer className="dashboard-footer">
            <span>CrowdQueue &middot; Real-time crowd &amp; queue management system</span>
          </footer>
        </main>
      </div>
    </div>
  );
}

function loadSavedSession() {
  try {
    const saved = window.localStorage.getItem("crowd_admin_session");
    return saved ? JSON.parse(saved) : null;
  } catch (err) {
    window.localStorage.removeItem("crowd_admin_session");
    return null;
  }
}
