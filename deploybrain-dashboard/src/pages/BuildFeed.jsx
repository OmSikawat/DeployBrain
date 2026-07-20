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

  if (loading) return <div className="p-6 text-gray-500 font-mono">Loading builds...</div>;

  return (
    <div className="p-6">
      <h1 className="text-xl font-semibold mb-4">Build Feed</h1>
      <div className="border border-border rounded-lg overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-panel text-gray-500 text-xs uppercase tracking-wide">
            <tr>
              <th className="text-left px-4 py-3">Repo</th>
              <th className="text-left px-4 py-3">Triggered</th>
              <th className="text-left px-4 py-3">Failure type</th>
              <th className="text-left px-4 py-3">Confidence</th>
              <th className="text-left px-4 py-3">Status</th>
              <th className="text-left px-4 py-3">PR</th>
            </tr>
          </thead>
          <tbody>
            {builds.map((b) => (
              <tr
                key={b.id}
                className="border-t border-border hover:bg-panel cursor-pointer"
                onClick={() => navigate(`/builds/${b.id}`)}
              >
                <td className="px-4 py-3 font-mono text-xs">{b.repoName}</td>
                <td className="px-4 py-3 text-gray-400">
                  {new Date(b.triggeredAt).toLocaleString()}
                </td>
                <td className="px-4 py-3 font-mono text-xs">{b.failureType || "—"}</td>
                <td className="px-4 py-3 font-mono text-xs">
                  {b.confidence ? `${Math.round(b.confidence * 100)}%` : "—"}
                </td>
                <td className="px-4 py-3">
                  <StatusBadge status={b.agentStatus || b.status} />
                </td>
                <td className="px-4 py-3">
                  {b.prUrl ? (
                    
                      <a href={b.prUrl}
                      target="_blank"
                      rel="noreferrer"
                      onClick={(e) => e.stopPropagation()}
                      className="text-signal-blue underline text-xs"
                    >
                      View PR
                    </a>
                  ) : (
                    "—>"
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}