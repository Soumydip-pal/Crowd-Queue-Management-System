export default function MetricCard({ label, value, icon: Icon, accent = "violet", hint }) {
  return (
    <article className={`metric-card accent-${accent}`}>
      <div className="metric-card-icon">{Icon ? <Icon /> : null}</div>
      <div className="metric-card-body">
        <span>{label}</span>
        <strong>{value}</strong>
        {hint && <em>{hint}</em>}
      </div>
    </article>
  );
}
