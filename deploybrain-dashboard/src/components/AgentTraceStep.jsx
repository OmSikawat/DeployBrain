import { useState } from "react";

const TOOL_ICONS = {
  read_file: "📄",
  search_logs: "🔍",
  get_diff: "🔀",
  get_history: "🕘",
  open_pr: "✅",
};

export default function AgentTraceStep({ step }) {
  const [expanded, setExpanded] = useState(false);

  return (
    <div className="border border-border rounded-lg bg-panel p-4 mb-3">
      <div
        className="flex items-center justify-between cursor-pointer"
        onClick={() => setExpanded(!expanded)}
      >
        <div className="flex items-center gap-3">
          <span className="text-xs font-mono text-gray-500">#{step.stepIndex}</span>
          {step.toolName ? (
            <span className="font-mono text-sm text-signal-blue">
              {TOOL_ICONS[step.toolName] || "🛠"} {step.toolName}
            </span>
          ) : (
            <span className="font-mono text-sm text-signal-green">💭 final reasoning</span>
          )}
        </div>
        <div className="flex items-center gap-2 text-xs text-gray-500 font-mono">
          {step.llmProvider && <span>{step.llmProvider}</span>}
          {step.durationMs && <span>{step.durationMs}ms</span>}
          <span>{expanded ? "▲" : "▼"}</span>
        </div>
      </div>

      {expanded && (
        <div className="mt-3 space-y-2 text-sm">
          {step.thought && (
            <div>
              <div className="text-xs uppercase tracking-wide text-gray-500 mb-1">Reasoning</div>
              <pre className="whitespace-pre-wrap font-mono text-xs bg-base rounded p-2 text-gray-300">
                {step.thought}
              </pre>
            </div>
          )}
          {step.toolInput && (
            <div>
              <div className="text-xs uppercase tracking-wide text-gray-500 mb-1">Input</div>
              <pre className="whitespace-pre-wrap font-mono text-xs bg-base rounded p-2 text-gray-300">
                {step.toolInput}
              </pre>
            </div>
          )}
          {step.toolOutput && (
            <div>
              <div className="text-xs uppercase tracking-wide text-gray-500 mb-1">Output</div>
              <pre className="whitespace-pre-wrap font-mono text-xs bg-base rounded p-2 text-gray-300 max-h-64 overflow-y-auto">
                {step.toolOutput}
              </pre>
            </div>
          )}
        </div>
      )}
    </div>
  );
}