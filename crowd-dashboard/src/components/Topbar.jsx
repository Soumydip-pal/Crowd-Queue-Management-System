import { FiMenu, FiMoon, FiSun, FiWifi, FiWifiOff } from "react-icons/fi";
import { useTheme } from "../context/ThemeContext";

export default function Topbar({ connectionState, onMenuClick, adminSession }) {
  const { dark, toggleTheme } = useTheme();
  const isLive = connectionState === "live";

  return (
    <header className="topbar">
      <div className="topbar-left">
        <button
          type="button"
          className="icon-button menu-toggle"
          onClick={onMenuClick}
          aria-label="Toggle navigation"
        >
          <FiMenu />
        </button>
        <div className="brand">
          <span className="brand-mark">CQ</span>
          <div className="brand-copy">
            <strong>CrowdQueue</strong>
            <span>Live Crowd &amp; Queue Management</span>
          </div>
        </div>
      </div>

      <div className="topbar-right">
        <div className={`connection-pill ${isLive ? "live" : "polling"}`}>
          {isLive ? <FiWifi /> : <FiWifiOff />}
          <span>{isLive ? "Live" : "Fallback"}</span>
        </div>

        {adminSession && (
          <div className="session-chip" title={adminSession.email}>
            <span className="session-avatar">{adminSession.email?.[0]?.toUpperCase() ?? "A"}</span>
            <span className="session-role">{adminSession.role}</span>
          </div>
        )}

        <button
          type="button"
          className="icon-button theme-toggle"
          onClick={toggleTheme}
          aria-label="Toggle color theme"
          title="Toggle color theme"
        >
          {dark ? <FiSun /> : <FiMoon />}
        </button>
      </div>
    </header>
  );
}
