import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { getBuilds } from "../services/api";
import StatusBadge from "../components/statusBadge";

export default function BuildFeed() {
  const [builds, setBuilds] = useState([]);
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();

  useEffect(() => {
    getBuilds(0, 20)
      .then((data) => setBuilds(data.content || []))
      .finally(() => setLoading(false));
  }, []);

  if (loading) {
    return (
      <div className="p-6 space-y-6 animate-pulse">
        <div className="flex items-center justify-between">
          <div className="h-7 w-36 bg-panel rounded border border-border"></div>
          <div className="h-5 w-24 bg-panel rounded border border-border"></div>
        </div>
        <div className="border border-border rounded-lg bg-panel/50 overflow-hidden">
          <div className="h-10 bg-panel border-b border-border"></div>
          <div className="divide-y divide-border">
            {[...Array(6)].map((_, i) => (
              <div key={i} className="h-14 bg-base/40 p-4 flex items-center justify-between gap-4">
                <div className="h-4 w-28 bg-panel rounded"></div>
                <div className="h-4 w-36 bg-panel rounded"></div>
                <div className="h-4 w-24 bg-panel rounded"></div>
                <div className="h-4 w-16 bg-panel rounded"></div>
                <div className="h-6 w-20 bg-panel rounded-full"></div>
                <div className="h-4 w-12 bg-panel rounded"></div>
              </div>
            ))}
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="p-6 space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold tracking-tight text-gray-100">Build Feed</h1>
          <p className="text-xs text-gray-400 font-mono mt-0.5">Real-time CI execution log & failure analysis</p>
        </div>
        <div className="text-xs font-mono text-gray-400 bg-panel px-3 py-1.5 rounded-md border border-border flex items-center gap-2">
          <span className="w-2 h-2 rounded-full bg-signal-blue animate-pulse"></span>
          <span>{builds.length} builds recorded</span>
        </div>
      </div>

      <div className="border border-border rounded-lg overflow-hidden bg-panel/30 shadow-sm">
        <table className="w-full text-sm">
          <thead className="bg-panel border-b border-border text-gray-400 text-[11px] font-mono uppercase tracking-wider">
            <tr>
              <th className="text-left px-4 py-3 font-medium">Repo</th>
              <th className="text-left px-4 py-3 font-medium">Triggered</th>
              <th className="text-left px-4 py-3 font-medium">Failure type</th>
              <th className="text-left px-4 py-3 font-medium">Confidence</th>
              <th className="text-left px-4 py-3 font-medium">Status</th>
              <th className="text-left px-4 py-3 font-medium">PR</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border/60">
            {builds.map((b) => (
              <tr
                key={b.id}
                className="hover:bg-panel/80 transition-colors duration-150 cursor-pointer group"
                onClick={() => navigate(`/builds/${b.id}`)}
              >
                <td className="px-4 py-3.5 font-mono text-xs font-medium text-gray-200 group-hover:text-signal-blue transition-colors">
                  {b.repoName}
                </td>
                <td className="px-4 py-3.5 text-xs text-gray-400 font-mono">
                  {new Date(b.triggeredAt).toLocaleString()}
                </td>
                <td className="px-4 py-3.5 font-mono text-xs text-gray-300">
                  {b.failureType ? (
                    <span className="px-2 py-0.5 rounded bg-base/80 border border-border text-gray-300">
                      {b.failureType}
                    </span>
                  ) : (
                    <span className="text-gray-600">—</span>
                  )}
                </td>
                <td className="px-4 py-3.5 font-mono text-xs text-gray-300">
                  {b.confidence ? (
                    <span className="text-signal-amber font-semibold">{Math.round(b.confidence * 100)}%</span>
                  ) : (
                    <span className="text-gray-600">—</span>
                  )}
                </td>
                <td className="px-4 py-3.5">
                  <StatusBadge status={b.agentStatus || b.status} />
                </td>
                <td className="px-4 py-3.5">
                  {b.prUrl ? (
                    <a
                      href={b.prUrl}
                      target="_blank"
                      rel="noreferrer"
                      onClick={(e) => e.stopPropagation()}
                      className="inline-flex items-center gap-1 text-signal-blue hover:text-blue-400 text-xs font-mono transition-colors border border-signal-blue/20 bg-signal-blue/10 px-2 py-0.5 rounded"
                    >
                      <span>View PR</span>
                      <span className="text-[10px]">↗</span>
                    </a>
                  ) : (
                    <span className="text-gray-600 font-mono text-xs">—</span>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>

        {builds.length === 0 && (
          <div className="p-12 text-center flex flex-col items-center justify-center space-y-3">
            <div className="w-12 h-12 rounded-full bg-base border border-border flex items-center justify-center text-gray-500 font-mono text-lg">
              ⚡
            </div>
            <div>
              <p className="text-sm font-medium text-gray-300 font-mono">No build records found</p>
              <p className="text-xs text-gray-500 mt-1">Build runs will appear here as CI/CD triggers execute.</p>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}