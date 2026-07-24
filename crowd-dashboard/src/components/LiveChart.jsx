import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

export default function LiveChart({ data }) {
  if (!data || data.length === 0) {
    return <p className="muted chart-empty">Waiting for live samples to chart…</p>;
  }

  return (
    <div className="chart-wrap">
      <ResponsiveContainer width="100%" height={220}>
        <AreaChart data={data} margin={{ top: 8, right: 12, left: -18, bottom: 0 }}>
          <defs>
            <linearGradient id="countGradient" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="var(--accent-violet)" stopOpacity={0.55} />
              <stop offset="100%" stopColor="var(--accent-violet)" stopOpacity={0.03} />
            </linearGradient>
            <linearGradient id="waitGradient" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="var(--accent-amber)" stopOpacity={0.5} />
              <stop offset="100%" stopColor="var(--accent-amber)" stopOpacity={0.02} />
            </linearGradient>
          </defs>
          <CartesianGrid strokeDasharray="3 3" stroke="var(--border-soft)" vertical={false} />
          <XAxis dataKey="time" tick={{ fontSize: 11, fill: "var(--text-muted)" }} minTickGap={24} />
          <YAxis tick={{ fontSize: 11, fill: "var(--text-muted)" }} width={32} />
          <Tooltip
            contentStyle={{
              background: "var(--surface)",
              border: "1px solid var(--border-soft)",
              borderRadius: 12,
              fontSize: 12,
            }}
          />
          <Area
            type="monotone"
            dataKey="count"
            name="Queue length"
            stroke="var(--accent-violet)"
            strokeWidth={2}
            fill="url(#countGradient)"
          />
          <Area
            type="monotone"
            dataKey="wait"
            name="Wait (min)"
            stroke="var(--accent-amber)"
            strokeWidth={2}
            fill="url(#waitGradient)"
          />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}
