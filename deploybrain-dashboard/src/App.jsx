import { BrowserRouter, Routes, Route, NavLink } from "react-router-dom";
import BuildFeed from "./pages/BuildFeed";
import Analytics from "./pages/Analytics";
import BuildDetail from "./pages/BuildDetail";
import PROutcomes from "./pages/PROutcomes";

function NavBar() {
  const linkClass = ({ isActive }) =>
    `px-3.5 py-1.5 text-xs font-mono rounded-md transition-all duration-150 flex items-center gap-1.5 ${
      isActive
        ? "bg-panel text-signal-blue font-medium border border-signal-blue/20 shadow-sm"
        : "text-gray-400 hover:text-gray-200 hover:bg-panel/50"
    }`;

  return (
    <nav className="border-b border-border bg-base/95 backdrop-blur-sm sticky top-0 z-50 px-6 py-3 flex items-center justify-between">
      <div className="flex items-center gap-6">
        <div className="flex items-center gap-2">
          <span className="text-signal-amber font-mono font-bold text-sm tracking-wider flex items-center gap-1.5">
            <span className="inline-block w-2 h-2 rounded-full bg-signal-amber animate-pulse"></span>
            ◆ DeployBrain
          </span>
        </div>
        <div className="flex items-center gap-1.5">
          <NavLink to="/" className={linkClass} end>Builds</NavLink>
          <NavLink to="/analytics" className={linkClass}>Analytics</NavLink>
          <NavLink to="/pr-outcomes" className={linkClass}>PR Outcomes</NavLink>
        </div>
      </div>
      <div className="flex items-center gap-2 text-xs font-mono text-gray-500">
        <span className="w-2 h-2 rounded-full bg-signal-green"></span>
        <span>CI Engine Online</span>
      </div>
    </nav>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <div className="min-h-screen bg-base text-gray-200 flex flex-col">
        <NavBar />
        <main className="flex-1 max-w-7xl w-full mx-auto">
          <Routes>
            <Route path="/" element={<BuildFeed />} />
            <Route path="/analytics" element={<Analytics />} />
            <Route path="/builds/:id" element={<BuildDetail />} />
            <Route path="/pr-outcomes" element={<PROutcomes />} />
          </Routes>
        </main>
      </div>
    </BrowserRouter>
  );
}