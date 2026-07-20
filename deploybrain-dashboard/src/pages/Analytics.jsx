import { useEffect, useState } from "react";
import { BarChart, Bar, LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from "recharts";
import { getFailureTypeStats, getMttrStats, getFixRateStats } from "../services/api";

function MetricCard({ label, value }) {
  return (
    <div className="border border-border rounded-lg bg-panel p-4">
      <div className="text-xs uppercase tracking-wide text-gray-500 mb-1">{label}</div>
      <div className="text-2xl font-mono text-gray-100">{value}</div>
    </div>
  );
}

export default function Analytics() {
  const [failureTypes, setFailureTypes] = useState(null);
  const [mttr, setMttr] = useState(null);
  const [fixRate, setFixRate] = useState(null);

  useEffect(() => {
    getFailureTypeStats().then(setFailureTypes);
    getMttrStats().then(setMttr);
    getFixRateStats().then(setFixRate);
  }, []);

  const failureTypeData = failureTypes
    ? Object.entries(failureTypes.countsByType).map(([type, count]) => ({ type, count }))
    : [];

  const mttrData = mttr
    ? mttr.trend.map((p) => ({ week: p.weekLabel, minutes: Math.round(p.avgMinutes) }))
    : [];

  return (
    <div className="p-6">
      <h1 className="text-xl font-semibold mb-4">Analytics</h1>

      <div className="grid grid-cols-4 gap-4 mb-6">
        <MetricCard label="Total builds" value={failureTypes?.totalBuilds ?? "—"} />
        <MetricCard
          label="Auto-fix rate"
          value={fixRate ? `${Math.round(fixRate.overallAutoFixRate * 100)}%` : "—"}
        />
        <MetricCard
          label="Avg MTTR"
          value={mttr ? `${Math.round(mttr.overallAvgMinutes)} min` : "—"}
        />
        <MetricCard label="Needs review" value={fixRate?.totalNeedsReview ?? "—"} />
      </div>

      <div className="grid grid-cols-2 gap-6">
        <div className="border border-border rounded-lg bg-panel p-4">
          <div className="text-sm text-gray-400 mb-3">Failure type distribution</div>
          <ResponsiveContainer width="100%" height={260}>
            <BarChart data={failureTypeData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#2A2E38" />
              <XAxis dataKey="type" tick={{ fontSize: 10, fill: "#9CA3AF" }} />
              <YAxis tick={{ fontSize: 10, fill: "#9CA3AF" }} />
              <Tooltip contentStyle={{ background: "#171A21", border: "1px solid #2A2E38" }} />
              <Bar dataKey="count" fill="#4A9EFF" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>

        <div className="border border-border rounded-lg bg-panel p-4">
          <div className="text-sm text-gray-400 mb-3">MTTR trend</div>
          <ResponsiveContainer width="100%" height={260}>
            <LineChart data={mttrData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#2A2E38" />
              <XAxis dataKey="week" tick={{ fontSize: 10, fill: "#9CA3AF" }} />
              <YAxis tick={{ fontSize: 10, fill: "#9CA3AF" }} />
              <Tooltip contentStyle={{ background: "#171A21", border: "1px solid #2A2E38" }} />
              <Line type="monotone" dataKey="minutes" stroke="#D9A441" strokeWidth={2} />
            </LineChart>
          </ResponsiveContainer>
        </div>
      </div>
    </div>
  );
}