import { BrowserRouter, Routes, Route, NavLink } from "react-router-dom";
import BuildFeed from "./pages/BuildFeed";
import Analytics from "./pages/Analytics";
import BuildDetail from "./pages/BuildDetail";
import PROutcomes from "./pages/PROutcomes";

function NavBar() {
  const linkClass = ({ isActive }) =>
    `px-4 py-2 text-sm font-mono rounded ${isActive ? "bg-panel text-signal-blue" : "text-gray-400 hover:text-gray-200"}`;

  return (
    <nav className="border-b border-border bg-base px-6 py-3 flex items-center gap-2">
      <span className="text-signal-amber font-mono font-semibold mr-6">◆ DeployBrain</span>
      <NavLink to="/" className={linkClass} end>Builds</NavLink>
      <NavLink to="/analytics" className={linkClass}>Analytics</NavLink>
      <NavLink to="/pr-outcomes" className={linkClass}>PR Outcomes</NavLink>
    </nav>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <NavBar />
      <Routes>
        <Route path="/" element={<BuildFeed />} />
        <Route path="/analytics" element={<Analytics />} />
        <Route path="/builds/:id" element={<BuildDetail />} />
        <Route path="/pr-outcomes" element={<PROutcomes />} />
      </Routes>
    </BrowserRouter>
  );
}