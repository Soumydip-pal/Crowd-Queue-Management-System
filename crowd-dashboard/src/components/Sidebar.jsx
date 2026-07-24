import {
  FiActivity,
  FiBarChart2,
  FiBell,
  FiClock,
  FiMapPin,
  FiSettings,
  FiX,
} from "react-icons/fi";

const NAV_LINKS = [
  { href: "#overview", label: "Overview", icon: FiActivity },
  { href: "#admin-console", label: "Admin Console", icon: FiSettings },
  { href: "#alerts", label: "Alerts", icon: FiBell },
  { href: "#analytics", label: "Analytics", icon: FiBarChart2 },
  { href: "#history", label: "Live History", icon: FiClock },
];

export default function Sidebar({
  open,
  onClose,
  counters,
  selectedCounterId,
  onSelectCounter,
  locationsCount,
}) {
  return (
    <>
      <div
        className={`sidebar-scrim ${open ? "visible" : ""}`}
        onClick={onClose}
        aria-hidden="true"
      />
      <aside className={`sidebar ${open ? "open" : ""}`}>
        <div className="sidebar-head">
          <span className="muted">Navigation</span>
          <button
            type="button"
            className="icon-button sidebar-close"
            onClick={onClose}
            aria-label="Close navigation"
          >
            <FiX />
          </button>
        </div>

        <div className="sidebar-block">
          <label className="sidebar-label">
            <FiMapPin /> Active counter
          </label>
          <select
            className="sidebar-select"
            value={selectedCounterId}
            onChange={(event) => onSelectCounter(Number(event.target.value))}
          >
            {counters.map((counter) => (
              <option key={counter.id} value={counter.id}>
                {counter.locationName} &middot; {counter.name}
              </option>
            ))}
            {counters.length === 0 && <option value="">No counters yet</option>}
          </select>
          <p className="sidebar-hint">
            {locationsCount} location{locationsCount === 1 ? "" : "s"} loaded
          </p>
        </div>

        <nav className="sidebar-nav">
          {NAV_LINKS.map(({ href, label, icon: Icon }) => (
            <a key={href} href={href} className="sidebar-link" onClick={onClose}>
              <Icon />
              <span>{label}</span>
            </a>
          ))}
        </nav>
      </aside>
    </>
  );
}
