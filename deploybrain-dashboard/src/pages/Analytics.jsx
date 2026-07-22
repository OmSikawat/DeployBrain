import { useEffect, useState } from "react";
import { BarChart, Bar, LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from "recharts";
import { getFailureTypeStats, getMttrStats, getFixRateStats } from "../services/api";

function MetricCard({ label, value, signalColor = "text-gray-100" }) {
  return (
    <div className="border border-border rounded-lg bg-panel p-4 shadow-sm transition-all duration-150 hover:border-gray-700">
      <div className="text-[11px] font-mono uppercase tracking-wider text-gray-400 mb-1">{label}</div>
      <div className={`text-2xl font-mono font-semibold ${signalColor}`}>{value}</div>
    </div>
  );
}

export default function Analytics() {
  const [failureTypes, setFailureTypes] = useState(null);
  const [mttr, setMttr] = useState(null);
  const [fixRate, setFixRate] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([
      getFailureTypeStats().then(setFailureTypes),
      getMttrStats().then(setMttr),
      getFixRateStats().then(setFixRate),
    ]).finally(() => setLoading(false));
  }, []);

  const failureTypeData = failureTypes
    ? Object.entries(failureTypes.countsByType).map(([type, count]) => ({ type, count }))
    : [];

  const mttrData = mttr
    ? mttr.trend.map((p) => ({ week: p.weekLabel, minutes: Math.round(p.avgMinutes) }))
    : [];

  if (loading) {
    return (
      <div className="p-6 space-y-6 animate-pulse">
        <div className="h-7 w-32 bg-panel rounded border border-border"></div>
        <div className="grid grid-cols-4 gap-4">
          {[...Array(4)].map((_, i) => (
            <div key={i} className="h-20 bg-panel rounded-lg border border-border"></div>
          ))}
        </div>
        <div className="grid grid-cols-2 gap-6">
          <div className="h-72 bg-panel rounded-lg border border-border"></div>
          <div className="h-72 bg-panel rounded-lg border border-border"></div>
        </div>
      </div>
    );
  }

  return (
    <div className="p-6 space-y-6">
      <div>
        <h1 className="text-xl font-bold tracking-tight text-gray-100">Analytics</h1>
        <p className="text-xs text-gray-400 font-mono mt-0.5">Automated triage metrics and MTTR performance</p>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <MetricCard label="Total builds" value={failureTypes?.totalBuilds ?? "—"} signalColor="text-signal-blue" />
        <MetricCard
          label="Auto-fix rate"
          value={fixRate ? `${Math.round(fixRate.overallAutoFixRate * 100)}%` : "—"}
          signalColor="text-signal-green"
        />
        <MetricCard
          label="Avg MTTR"
          value={mttr ? `${Math.round(mttr.overallAvgMinutes)} min` : "—"}
          signalColor="text-signal-amber"
        />
        <MetricCard
          label="Needs review"
          value={fixRate?.totalNeedsReview ?? "—"}
          signalColor="text-gray-100"
        />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="border border-border rounded-lg bg-panel p-5 shadow-sm">
          <div className="flex items-center justify-between mb-4">
            <div className="text-xs font-mono uppercase tracking-wider text-gray-400 flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-signal-blue"></span>
              Failure type distribution
            </div>
          </div>
          <ResponsiveContainer width="100%" height={260}>
            <BarChart data={failureTypeData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#2A2E38" vertical={false} />
              <XAxis dataKey="type" tick={{ fontSize: 10, fill: "#9CA3AF" }} axisLine={{ stroke: "#2A2E38" }} />
              <YAxis tick={{ fontSize: 10, fill: "#9CA3AF" }} axisLine={{ stroke: "#2A2E38" }} />
              <Tooltip
                contentStyle={{ background: "#0F1115", borderColor: "#2A2E38", borderRadius: "6px", fontSize: "12px" }}
                cursor={{ fill: "rgba(255, 255, 255, 0.03)" }}
              />
              <Bar dataKey="count" fill="#4A9EFF" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>

        <div className="border border-border rounded-lg bg-panel p-5 shadow-sm">
          <div className="flex items-center justify-between mb-4">
            <div className="text-xs font-mono uppercase tracking-wider text-gray-400 flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-signal-amber"></span>
              MTTR trend (minutes)
            </div>
          </div>
          <ResponsiveContainer width="100%" height={260}>
            <LineChart data={mttrData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#2A2E38" vertical={false} />
              <XAxis dataKey="week" tick={{ fontSize: 10, fill: "#9CA3AF" }} axisLine={{ stroke: "#2A2E38" }} />
              <YAxis tick={{ fontSize: 10, fill: "#9CA3AF" }} axisLine={{ stroke: "#2A2E38" }} />
              <Tooltip
                contentStyle={{ background: "#0F1115", borderColor: "#2A2E38", borderRadius: "6px", fontSize: "12px" }}
              />
              <Line type="monotone" dataKey="minutes" stroke="#D9A441" strokeWidth={2} dot={{ fill: "#D9A441", r: 3 }} />
            </LineChart>
          </ResponsiveContainer>
        </div>
      </div>
    </div>
  );
}