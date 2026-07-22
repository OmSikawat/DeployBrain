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

  if (!build) {
    return (
      <div className="p-6 grid grid-cols-2 gap-6 animate-pulse">
        <div className="space-y-4">
          <div className="h-7 w-32 bg-panel rounded border border-border"></div>
          <div className="border border-border rounded-lg bg-panel p-5 space-y-4">
            {[...Array(6)].map((_, i) => (
              <div key={i} className="flex justify-between items-center">
                <div className="h-4 w-20 bg-base rounded"></div>
                <div className="h-4 w-32 bg-base rounded"></div>
              </div>
            ))}
          </div>
        </div>
        <div className="space-y-4">
          <div className="h-7 w-40 bg-panel rounded border border-border"></div>
          <div className="space-y-3">
            {[...Array(3)].map((_, i) => (
              <div key={i} className="h-20 bg-panel rounded-lg border border-border"></div>
            ))}
          </div>
        </div>
      </div>
    );
  }

  // merge persisted steps (loaded once) with any new live steps streamed in
  const persistedSteps = build.traceSteps || [];
  const seenIndexes = new Set(persistedSteps.map((s) => s.stepIndex));
  const combinedSteps = [...persistedSteps, ...liveSteps.filter((s) => !seenIndexes.has(s.stepIndex))]
    .sort((a, b) => a.stepIndex - b.stepIndex);

  return (
    <div className="p-6 space-y-6">
      <div className="flex items-center justify-between border-b border-border/60 pb-4">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-xl font-bold tracking-tight text-gray-100">Build #{build.id}</h1>
            <StatusBadge status={build.agentStatus || build.status} />
          </div>
          <p className="text-xs text-gray-400 font-mono mt-1 flex items-center gap-2">
            <span>Repo:</span>
            <span className="text-signal-blue">{build.repoName}</span>
            <span>•</span>
            <span>Workflow:</span>
            <span className="text-gray-300">{build.workflowName}</span>
          </p>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div>
          <h2 className="text-sm font-mono uppercase tracking-wider text-gray-400 mb-3 flex items-center gap-2">
            <span className="w-2 h-2 rounded-full bg-signal-blue"></span>
            Build Info
          </h2>
          <div className="border border-border rounded-lg bg-panel p-5 space-y-3.5 text-sm shadow-sm">
            <Row label="Repo" value={build.repoName} mono />
            <Row label="Workflow" value={build.workflowName} />
            <Row label="Commit" value={build.commitSha?.slice(0, 12)} mono />
            <Row label="Failure type" value={build.failureType || "—"} mono />
            <Row label="Confidence" value={build.confidence ? `${Math.round(build.confidence * 100)}%` : "—"} />
            <Row label="Status" value={<StatusBadge status={build.agentStatus || build.status} />} />
            <Row label="LLM provider" value={build.llmProviderUsed || "—"} mono />
            {build.diagnosis && (
              <div className="pt-2 border-t border-border/50">
                <div className="text-[11px] font-mono uppercase tracking-wider text-gray-400 mb-1">Diagnosis</div>
                <p className="text-gray-300 text-xs leading-relaxed bg-base/60 p-2.5 rounded border border-border/50">{build.diagnosis}</p>
              </div>
            )}
            {build.rootCause && (
              <div className="pt-2 border-t border-border/50">
                <div className="text-[11px] font-mono uppercase tracking-wider text-gray-400 mb-1">Root cause</div>
                <p className="text-gray-300 text-xs leading-relaxed bg-base/60 p-2.5 rounded border border-border/50">{build.rootCause}</p>
              </div>
            )}
            {build.evidenceLines && (
              <div className="pt-2 border-t border-border/50">
                <div className="text-[11px] font-mono uppercase tracking-wider text-gray-400 mb-1">Evidence</div>
                <pre className="whitespace-pre-wrap font-mono text-xs bg-base rounded-md p-3 text-signal-red/90 border border-signal-red/20 overflow-x-auto max-h-48 overflow-y-auto">
                  {build.evidenceLines}
                </pre>
              </div>
            )}
            {build.prUrl && (
              <div className="pt-3 border-t border-border/50">
                <a
                  href={build.prUrl}
                  target="_blank"
                  rel="noreferrer"
                  className="inline-flex items-center gap-2 text-signal-blue hover:text-blue-400 font-mono text-xs font-semibold bg-signal-blue/15 border border-signal-blue/30 px-3 py-2 rounded-md transition-colors"
                >
                  <span>Open Pull Request</span>
                  <span>→</span>
                </a>
              </div>
            )}
          </div>
        </div>

        <div>
          <div className="flex items-center justify-between mb-3">
            <h2 className="text-sm font-mono uppercase tracking-wider text-gray-400 flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-signal-amber animate-pulse"></span>
              Agent Reasoning
            </h2>
            <span className="text-xs font-mono text-gray-500">
              {combinedSteps.length} step{combinedSteps.length !== 1 ? "s" : ""}
            </span>
          </div>

          {combinedSteps.length === 0 ? (
            <div className="border border-border rounded-lg bg-panel p-8 text-center flex flex-col items-center justify-center space-y-3">
              <div className="w-10 h-10 rounded-full bg-base border border-border flex items-center justify-center text-gray-500 font-mono">
                🤖
              </div>
              <div>
                <p className="text-sm font-medium font-mono text-gray-300">No agent activity yet</p>
                <p className="text-xs text-gray-500 mt-1">Trace steps will stream live here as the agent analyzes the failure.</p>
              </div>
            </div>
          ) : (
            <div className="space-y-3">
              {combinedSteps.map((step) => <AgentTraceStep key={step.stepIndex} step={step} />)}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function Row({ label, value, mono }) {
  return (
    <div className="flex justify-between items-center py-0.5">
      <span className="text-[11px] font-mono uppercase tracking-wider text-gray-400">{label}</span>
      <span className={mono ? "font-mono text-xs text-gray-200" : "text-xs text-gray-200 font-medium"}>{value}</span>
    </div>
  );
}