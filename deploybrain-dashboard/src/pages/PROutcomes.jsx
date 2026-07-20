import { useEffect, useState } from "react";
import { getBuilds } from "../services/api";

export default function PROutcomes() {
  const [prBuilds, setPrBuilds] = useState([]);

  useEffect(() => {
    getBuilds(0, 100).then((data) => {
      setPrBuilds((data.content || []).filter((b) => b.prUrl));
    });
  }, []);

  return (
    <div className="p-6">
      <h1 className="text-xl font-semibold mb-4">Pull Request Outcomes</h1>
      <div className="border border-border rounded-lg overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-panel text-gray-500 text-xs uppercase tracking-wide">
            <tr>
              <th className="text-left px-4 py-3">Repo</th>
              <th className="text-left px-4 py-3">Failure type</th>
              <th className="text-left px-4 py-3">LLM provider</th>
              <th className="text-left px-4 py-3">Triggered</th>
              <th className="text-left px-4 py-3">PR</th>
            </tr>
          </thead>
          <tbody>
            {prBuilds.map((b) => (
              <tr key={b.id} className="border-t border-border">
                <td className="px-4 py-3 font-mono text-xs">{b.repoName}</td>
                <td className="px-4 py-3 font-mono text-xs">{b.failureType}</td>
                <td className="px-4 py-3 font-mono text-xs">{b.llmProviderUsed}</td>
                <td className="px-4 py-3 text-gray-400">{new Date(b.triggeredAt).toLocaleDateString()}</td>
                <td className="px-4 py-3">
                  <a href={b.prUrl} target="_blank" rel="noreferrer" className="text-signal-blue underline text-xs">
                    View
                  </a>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {prBuilds.length === 0 && (
          <div className="p-6 text-gray-500 text-sm font-mono">No pull requests opened yet.</div>
        )}
      </div>
    </div>
  );
}