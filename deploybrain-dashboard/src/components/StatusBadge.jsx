export default function StatusBadge({ status }) {
    const styles = {
      AGENT_COMPLETE: "bg-signal-green/15 text-signal-green border-signal-green/30",
      FIX_GENERATED: "bg-signal-green/15 text-signal-green border-signal-green/30",
      NEEDS_REVIEW: "bg-signal-amber/15 text-signal-amber border-signal-amber/30",
      AGENT_RUNNING: "bg-signal-blue/15 text-signal-blue border-signal-blue/30",
      AGENT_PENDING: "bg-signal-blue/15 text-signal-blue border-signal-blue/30",
      ERROR: "bg-signal-red/15 text-signal-red border-signal-red/30",
      CLASSIFICATION_FAILED: "bg-signal-red/15 text-signal-red border-signal-red/30",
    };
  
    const labels = {
      AGENT_COMPLETE: "Auto-fixed",
      FIX_GENERATED: "Auto-fixed",
      NEEDS_REVIEW: "Needs review",
      AGENT_RUNNING: "Running",
      AGENT_PENDING: "Pending",
      ERROR: "Error",
      CLASSIFICATION_FAILED: "Classification failed",
    };
  
    const style = styles[status] || "bg-gray-700/30 text-gray-400 border-gray-600";
    const label = labels[status] || status;
  
    return (
      <span className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-mono border ${style}`}>
        {label}
      </span>
    );
  }