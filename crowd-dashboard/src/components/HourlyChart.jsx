import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

export default function HourlyChart({ data }) {
  if (!data || data.length === 0) {
    return <p className="muted chart-empty">No queue history available for this window.</p>;
  }

  const chartData = data.map((row) => ({
    ...row,
    label: `${String(row.hour).padStart(2, "0")}:00`,
  }));

  return (
    <div className="chart-wrap">
      <ResponsiveContainer width="100%" height={220}>
        <BarChart data={chartData} margin={{ top: 8, right: 12, left: -18, bottom: 0 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="var(--border-soft)" vertical={false} />
          <XAxis dataKey="label" tick={{ fontSize: 11, fill: "var(--text-muted)" }} minTickGap={16} />
          <YAxis tick={{ fontSize: 11, fill: "var(--text-muted)" }} width={32} />
          <Tooltip
            contentStyle={{
              background: "var(--surface)",
              border: "1px solid var(--border-soft)",
              borderRadius: 12,
              fontSize: 12,
            }}
          />
          <Bar dataKey="averageCrowdLength" name="Avg crowd" fill="var(--accent-teal)" radius={[6, 6, 0, 0]} />
          <Bar dataKey="averagePredictedWaitMin" name="Avg wait (min)" fill="var(--accent-pink)" radius={[6, 6, 0, 0]} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
