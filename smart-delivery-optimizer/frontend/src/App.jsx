import { useState } from "react";

import KpiComparison from "./pages/KpiComparison";
import Optimize from "./pages/Optimize";
import DataQuality from "./pages/DataQuality";
import Operations from "./pages/Dashboard";
import Orders from "./pages/Orders";
import Vehicles from "./pages/Vehicles";
import Trips from "./pages/Trips";
import Unallocated from "./pages/Unallocated";

const PAGES = [
  { name: "Dashboard", component: KpiComparison },
  { name: "Optimize", component: Optimize },
  { name: "Data Quality", component: DataQuality },
  { name: "Operations", component: Operations },
  { name: "Orders", component: Orders },
  { name: "Vehicles", component: Vehicles },
  { name: "Trips", component: Trips },
  { name: "Unallocated", component: Unallocated },
];

function App() {
  const [activePage, setActivePage] = useState("Dashboard");

  // Each page fetches its data on mount, so navigating after a run
  // always shows the latest results.
  const page = PAGES.find((p) => p.name === activePage) || PAGES[0];
  const Page = page.component;

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand">
          <div className="brand-title">LogiSmart</div>
          <div className="brand-subtitle">
            Delivery Route &amp; Resource Optimization
          </div>
        </div>
      </header>

      <div className="app-body">
        <aside className="sidebar">
          <nav>
            {PAGES.map(({ name }) => (
              <button
                key={name}
                className={`nav-item ${activePage === name ? "active" : ""}`}
                onClick={() => setActivePage(name)}
              >
                {name}
              </button>
            ))}
          </nav>
        </aside>

        <main className="main-content">
          <Page key={page.name} onNavigate={setActivePage} />
        </main>
      </div>
    </div>
  );
}

export default App;
