import { useEffect, useState } from "react";
import { api } from "../services/api";

/*
 * Control screen: set optimization parameters, run the optimizer and
 * compare runs side by side (run history is kept for this browser tab).
 */

const HISTORY_KEY = "logismart.runHistory";

const PRESETS = {
  "v2 (recommended)": {},
  "v1.0-like: single trip, exact type": {
    multiTrip: false,
    allowVehicleUpgrade: false,
  },
  "Soft time windows": { enforceTimeWindows: false },
  "Minimise distance": { objective: "DISTANCE" },
};

function loadHistory() {
  try {
    return JSON.parse(sessionStorage.getItem(HISTORY_KEY) || "[]");
  } catch {
    return [];
  }
}

function saveHistory(history) {
  try {
    sessionStorage.setItem(HISTORY_KEY, JSON.stringify(history.slice(0, 10)));
  } catch {
    // storage unavailable - history is a convenience only
  }
}

function fmt(value, decimals = 0) {
  return Number(value || 0).toLocaleString("en-IN", {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  });
}

function describeParams(p) {
  return [
    p.objective,
    `${p.averageSpeedKmph} km/h`,
    p.multiTrip ? "multi-trip" : "single trip",
    p.allowVehicleUpgrade ? "upgrade" : "exact type",
    p.enforceTimeWindows ? "hard windows" : "soft windows",
    p.twoOpt ? "2-opt" : "no 2-opt",
  ].join(" · ");
}

function NumberField({ label, hint, value, onChange, step = 1, min, max }) {
  return (
    <label className="form-field">
      <span className="form-label">{label}</span>
      <input
        type="number"
        value={value}
        step={step}
        min={min}
        max={max}
        onChange={(e) => onChange(e.target.value === "" ? "" : Number(e.target.value))}
      />
      {hint && <span className="form-hint">{hint}</span>}
    </label>
  );
}

function Toggle({ label, hint, checked, onChange }) {
  return (
    <label className="form-toggle">
      <input type="checkbox" checked={checked} onChange={(e) => onChange(e.target.checked)} />
      <span>
        <span className="form-label">{label}</span>
        <span className="form-hint">{hint}</span>
      </span>
    </label>
  );
}

function Optimize({ onNavigate }) {
  const [defaults, setDefaults] = useState(null);
  const [params, setParams] = useState(null);
  const [latest, setLatest] = useState(null);
  const [history, setHistory] = useState(loadHistory);
  const [running, setRunning] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  useEffect(() => {
    let mounted = true;

    Promise.all([api.getDefaultParameters(), api.getLatestRun()])
      .then(([d, run]) => {
        if (!mounted) return;
        setDefaults(d);
        setParams(run?.parameters || d);
        setLatest(run);
      })
      .catch((err) => mounted && setError(err.message));

    return () => {
      mounted = false;
    };
  }, []);

  const set = (field) => (value) => setParams((p) => ({ ...p, [field]: value }));

  async function runOptimization() {
    setRunning(true);
    setError("");
    setMessage("");

    try {
      const run = await api.runOptimization(params);
      const summary = await api.getOptimizationSummary();

      const entry = {
        runId: run.runId,
        at: run.generatedAt,
        params: run.parameters,
        allocated: summary.ordersAllocated,
        total: summary.baselineOrders,
        distance: summary.totalDistanceKm,
        cost: summary.totalCostInr,
        days: summary.planningDays,
        onTime: summary.onTimeRatePercent,
        loadUtil: summary.averageLoadUtilizationPercent,
        runtime: run.optimizerRuntimeMs,
      };

      const next = [entry, ...history];
      setHistory(next);
      saveHistory(next);
      setLatest(run);
      setMessage(
        `Run complete: ${fmt(run.ordersAllocated)} orders on ${fmt(run.trips)} trips over ${run.planningDays} days in ${fmt(run.optimizerRuntimeMs)} ms.`
      );
    } catch (err) {
      setError(err.message);
    } finally {
      setRunning(false);
    }
  }

  if (!params) {
    return (
      <div className="page">
        <div className="page-header">
          <div>
            <h1>Optimize</h1>
            <p>Configure and run the route optimizer</p>
          </div>
        </div>
        {error ? (
          <div className="error-message">
            <strong>Unable to load parameters.</strong>
            <p>{error}</p>
          </div>
        ) : (
          <div className="loading-state">Loading parameters...</div>
        )}
      </div>
    );
  }

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>Optimize</h1>
          <p>Configure constraints and objective, then run the optimizer on the loaded dataset.</p>
        </div>
      </div>

      <div className="optimize-layout">
        <section className="dashboard-panel">
          <div className="panel-header">
            <h2>Parameters</h2>
            <div className="preset-row">
              {Object.entries(PRESETS).map(([name, overrides]) => (
                <button
                  key={name}
                  className="secondary-button"
                  onClick={() => setParams({ ...defaults, ...overrides })}
                  disabled={running}
                >
                  {name}
                </button>
              ))}
            </div>
          </div>

          <div className="form-grid">
            <label className="form-field">
              <span className="form-label">Objective</span>
              <select value={params.objective} onChange={(e) => set("objective")(e.target.value)}>
                <option value="COST">Minimise operating cost (₹)</option>
                <option value="DISTANCE">Minimise distance (km)</option>
              </select>
              <span className="form-hint">Score used to pick the best insertion</span>
            </label>

            <NumberField
              label="Average speed (km/h)"
              hint="Converts km to travel minutes"
              value={params.averageSpeedKmph}
              onChange={set("averageSpeedKmph")}
              min={1}
              max={120}
            />

            <label className="form-field">
              <span className="form-label">Day start</span>
              <input
                type="time"
                value={params.dayStartTime}
                onChange={(e) => set("dayStartTime")(e.target.value)}
              />
              <span className="form-hint">Shift = start + Max_Working_Minutes</span>
            </label>

            <NumberField
              label="Reload time (min)"
              hint="Hub turnaround between trips"
              value={params.reloadTimeMin}
              onChange={set("reloadTimeMin")}
              min={0}
              max={240}
            />

            <NumberField
              label="Max planning days"
              hint="Planning horizon limit"
              value={params.maxPlanningDays}
              onChange={set("maxPlanningDays")}
              min={1}
              max={365}
            />
          </div>

          <div className="toggle-grid">
            <Toggle
              label="Multi-trip per day"
              hint="Vehicles return, reload and go out again"
              checked={params.multiTrip}
              onChange={set("multiTrip")}
            />
            <Toggle
              label="Allow larger vehicle class"
              hint="Required type is the minimum (data dictionary)"
              checked={params.allowVehicleUpgrade}
              onChange={set("allowVehicleUpgrade")}
            />
            <Toggle
              label="Enforce delivery windows"
              hint="Off = allow late arrivals and report delay"
              checked={params.enforceTimeWindows}
              onChange={set("enforceTimeWindows")}
            />
            <Toggle
              label="2-opt improvement"
              hint="Reverse stop sub-sequences when shorter"
              checked={params.twoOpt}
              onChange={set("twoOpt")}
            />
          </div>

          <div className="form-actions">
            <button className="primary-button" onClick={runOptimization} disabled={running}>
              {running ? "Running..." : "Run optimization"}
            </button>
            {message && (
              <span className="form-success">
                {message}{" "}
                <a href="#" onClick={(e) => { e.preventDefault(); onNavigate?.("Dashboard"); }}>
                  View dashboard →
                </a>
              </span>
            )}
          </div>

          {error && (
            <div className="error-message">
              <strong>Run failed.</strong>
              <p>{error}</p>
            </div>
          )}
        </section>

        <section className="dashboard-panel">
          <div className="panel-header">
            <h2>Current Plan</h2>
          </div>
          {latest && (
            <>
              <div className="summary-row"><span>Run</span><strong>{latest.runId.slice(0, 8)}</strong></div>
              <div className="summary-row"><span>Data source</span><strong>{latest.dataSource}</strong></div>
              <div className="summary-row"><span>Generated</span><strong>{new Date(latest.generatedAt).toLocaleString()}</strong></div>
              <div className="summary-row"><span>Orders allocated</span><strong>{fmt(latest.ordersAllocated)}</strong></div>
              <div className="summary-row"><span>Unallocated</span><strong>{fmt(latest.ordersUnallocated)}</strong></div>
              <div className="summary-row"><span>Trips / days</span><strong>{fmt(latest.trips)} / {latest.planningDays}</strong></div>
              <div className="summary-row">
                <span>Validation</span>
                <strong>{latest.validationErrors} errors · {latest.validationWarnings} warnings</strong>
              </div>
              <div className="summary-row"><span>Optimizer runtime</span><strong>{fmt(latest.optimizerRuntimeMs)} ms</strong></div>
              <p className="form-hint">{describeParams(latest.parameters)}</p>
            </>
          )}
        </section>
      </div>

      <section className="dashboard-section">
        <div className="section-heading">
          <h2>Run History</h2>
          <p>Compare parameter settings (this browser tab only)</p>
        </div>

        <div className="table-container">
          <table className="data-table">
            <thead>
              <tr>
                <th>Time</th>
                <th>Settings</th>
                <th>Allocated</th>
                <th>Distance</th>
                <th>Cost</th>
                <th>Days</th>
                <th>On-time</th>
                <th>Load util.</th>
                <th>Runtime</th>
              </tr>
            </thead>
            <tbody>
              {history.length === 0 && (
                <tr>
                  <td colSpan="9" className="empty-table">
                    No runs yet in this session. Run the optimizer to start a comparison.
                  </td>
                </tr>
              )}
              {history.map((h) => (
                <tr key={h.runId}>
                  <td>{new Date(h.at).toLocaleTimeString()}</td>
                  <td>{describeParams(h.params)}</td>
                  <td>{fmt(h.allocated)} / {fmt(h.total)}</td>
                  <td>{fmt(h.distance)} km</td>
                  <td>₹{fmt(h.cost)}</td>
                  <td>{h.days}</td>
                  <td>{fmt(h.onTime, 1)}%</td>
                  <td>{fmt(h.loadUtil, 1)}%</td>
                  <td>{fmt(h.runtime)} ms</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}

export default Optimize;
