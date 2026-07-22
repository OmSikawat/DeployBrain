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
    <div className="border border-border rounded-lg bg-panel p-4 mb-3 transition-all duration-150 hover:border-gray-700 animate-fade-slide-in shadow-sm">
      <div
        className="flex items-center justify-between cursor-pointer select-none group"
        onClick={() => setExpanded(!expanded)}
      >
        <div className="flex items-center gap-3">
          <span className="text-xs font-mono text-gray-500 bg-base px-2 py-0.5 rounded border border-border">
            #{step.stepIndex}
          </span>
          {step.toolName ? (
            <span className="font-mono text-sm text-signal-blue font-medium flex items-center gap-1.5 group-hover:text-blue-400 transition-colors">
              <span>{TOOL_ICONS[step.toolName] || "🛠"}</span>
              <span>{step.toolName}</span>
            </span>
          ) : (
            <span className="font-mono text-sm text-signal-green font-medium flex items-center gap-1.5 group-hover:text-green-400 transition-colors">
              <span>💭</span>
              <span>final reasoning</span>
            </span>
          )}
        </div>
        <div className="flex items-center gap-3 text-xs text-gray-400 font-mono">
          {step.llmProvider && (
            <span className="px-2 py-0.5 rounded bg-base/60 border border-border text-gray-400">
              {step.llmProvider}
            </span>
          )}
          {step.durationMs && (
            <span className="text-gray-500">{step.durationMs}ms</span>
          )}
          <span className={`text-gray-400 transition-transform duration-200 ${expanded ? "rotate-180" : "rotate-0"}`}>
            ▼
          </span>
        </div>
      </div>

      {expanded && (
        <div className="mt-4 pt-3 border-t border-border/60 space-y-3 text-sm animate-fade-slide-in">
          {step.thought && (
            <div>
              <div className="text-[11px] font-mono uppercase tracking-wider text-gray-400 mb-1.5 flex items-center gap-1.5">
                <span className="w-1.5 h-1.5 rounded-full bg-signal-amber"></span>
                Reasoning
              </div>
              <pre className="whitespace-pre-wrap font-mono text-xs bg-base rounded-md p-3 text-gray-300 border border-border/50 overflow-x-auto">
                {step.thought}
              </pre>
            </div>
          )}
          {step.toolInput && (
            <div>
              <div className="text-[11px] font-mono uppercase tracking-wider text-gray-400 mb-1.5 flex items-center gap-1.5">
                <span className="w-1.5 h-1.5 rounded-full bg-signal-blue"></span>
                Input
              </div>
              <pre className="whitespace-pre-wrap font-mono text-xs bg-base rounded-md p-3 text-gray-300 border border-border/50 overflow-x-auto">
                {step.toolInput}
              </pre>
            </div>
          )}
          {step.toolOutput && (
            <div>
              <div className="text-[11px] font-mono uppercase tracking-wider text-gray-400 mb-1.5 flex items-center gap-1.5">
                <span className="w-1.5 h-1.5 rounded-full bg-signal-green"></span>
                Output
              </div>
              <pre className="whitespace-pre-wrap font-mono text-xs bg-base rounded-md p-3 text-gray-300 border border-border/50 max-h-64 overflow-y-auto">
                {step.toolOutput}
              </pre>
            </div>
          )}
        </div>
      )}
    </div>
  );
}