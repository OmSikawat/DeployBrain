import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { getBuildDetail } from "../services/api";
import { useWebSocket } from "../hooks/useWebSocket";
import AgentTraceStep from "../components/AgentTraceStep";
import StatusBadge from "../components/statusBadge";

export default function BuildDetail() {
  const { id } = useParams();
  const [build, setBuild] = useState(null);
  const { traceSteps: liveSteps } = useWebSocket(build?.failureId);

  useEffect(() => {
    getBuildDetail(id).then(setBuild);
  }, [id]);

  if (!build) return <div className="p-6 text-gray-500 font-mono">Loading...</div>;

  // merge persisted steps (loaded once) with any new live steps streamed in
  const persistedSteps = build.traceSteps || [];
  const seenIndexes = new Set(persistedSteps.map((s) => s.stepIndex));
  const combinedSteps = [...persistedSteps, ...liveSteps.filter((s) => !seenIndexes.has(s.stepIndex))]
    .sort((a, b) => a.stepIndex - b.stepIndex);

  return (
    <div className="p-6 grid grid-cols-2 gap-6">
      <div>
        <h1 className="text-xl font-semibold mb-4">Build Info</h1>
        <div className="border border-border rounded-lg bg-panel p-4 space-y-3 text-sm">
          <Row label="Repo" value={build.repoName} mono />
          <Row label="Workflow" value={build.workflowName} />
          <Row label="Commit" value={build.commitSha?.slice(0, 12)} mono />
          <Row label="Failure type" value={build.failureType || "—"} mono />
          <Row label="Confidence" value={build.confidence ? `${Math.round(build.confidence * 100)}%` : "—"} />
          <Row label="Status" value={<StatusBadge status={build.agentStatus || build.status} />} />
          <Row label="LLM provider" value={build.llmProviderUsed || "—"} mono />
          {build.diagnosis && (
            <div>
              <div className="text-xs uppercase tracking-wide text-gray-500 mb-1">Diagnosis</div>
              <p className="text-gray-300">{build.diagnosis}</p>
            </div>
          )}
          {build.rootCause && (
            <div>
              <div className="text-xs uppercase tracking-wide text-gray-500 mb-1">Root cause</div>
              <p className="text-gray-300">{build.rootCause}</p>
            </div>
          )}
          {build.evidenceLines && (
            <div>
              <div className="text-xs uppercase tracking-wide text-gray-500 mb-1">Evidence</div>
              <pre className="whitespace-pre-wrap font-mono text-xs bg-base rounded p-2 text-gray-400">
                {build.evidenceLines}
              </pre>
            </div>
          )}
          {build.prUrl && (
            <a href={build.prUrl} target="_blank" rel="noreferrer" className="text-signal-blue underline text-sm block">
              Open Pull Request →
            </a>
          )}
        </div>
      </div>

      <div>
        <h1 className="text-xl font-semibold mb-4">Agent Reasoning</h1>
        {combinedSteps.length === 0 ? (
          <div className="text-gray-500 font-mono text-sm">No agent activity yet for this build.</div>
        ) : (
          combinedSteps.map((step) => <AgentTraceStep key={step.stepIndex} step={step} />)
        )}
      </div>
    </div>
  );
}

function Row({ label, value, mono }) {
  return (
    <div className="flex justify-between items-center">
      <span className="text-xs uppercase tracking-wide text-gray-500">{label}</span>
      <span className={mono ? "font-mono text-xs text-gray-300" : "text-gray-300"}>{value}</span>
    </div>
  );
}