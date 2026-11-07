import { useEffect, useMemo, useState } from "react";
import { api } from "../services/api";

/*
 * Dashboard: baseline vs optimized KPI comparison.
 *
 * Every status, sign and colour on this page is derived from the numbers
 * returned by /api/optimization/summary (KpiCalculator on the backend).
 * Nothing is hard-coded as "Improved".
 */

function formatValue(value, unit) {
  const n = Number(value || 0);

  switch (unit) {
    case "INR":
      return `₹${n.toLocaleString("en-IN", { maximumFractionDigits: 0 })}`;
    case "%":
      return `${n.toFixed(2)}%`;
    case "orders":
    case "vehicles":
    case "days":
      return `${n.toLocaleString("en-IN", { maximumFractionDigits: 0 })} ${unit}`;
    default:
      return `${n.toLocaleString("en-IN", {
        minimumFractionDigits: 1,
        maximumFractionDigits: 1,
      })} ${unit}`;
  }
}

function formatNumber(value, decimals = 0) {
  return Number(value || 0).toLocaleString("en-IN", {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  });
}

const STATUS = {
  IMPROVED: { rag: "green", label: "Improved" },
  WORSENED: { rag: "red", label: "Worsened" },
  UNCHANGED: { rag: "amber", label: "Unchanged" },
  INFO: { rag: "amber", label: "Context" },
};

const DIRECTION_LABEL = {
  HIGHER_IS_BETTER: "Higher is better",
  LOWER_IS_BETTER: "Lower is better",
  NEUTRAL: "Context",
};

function RagIndicator({ status }) {
  return <span className={`rag-indicator ${status}`} />;
}

function KpiTooltip({ row }) {
  return (
    <span className="kpi-tooltip-wrapper">
      <span className="kpi-tooltip-icon" aria-label="Show KPI calculation">
        ?
      </span>
      <span className="kpi-tooltip">
        <span className="kpi-tooltip-line">
          <strong>Baseline:</strong> {row.baselineDefinition}
        </span>
        <span className="kpi-tooltip-line">
          <strong>Optimized:</strong> {row.optimizedDefinition}
        </span>
      </span>
    </span>
  );
}

function ChangeCell({ row }) {
  if (row.changePercent === null || row.changePercent === undefined) {
    return <td className="change-cell not-available">—</td>;
  }

  const sign = row.changePercent > 0 ? "+" : row.changePercent < 0 ? "−" : "";
  const cls =
    row.status === "IMPROVED"
      ? "positive"
      : row.status === "WORSENED"
        ? "negative"
        : "";

  return (
    <td className={`change-cell ${cls}`}>
      {sign}
      {Math.abs(row.changePercent).toFixed(1)}%
    </td>
  );
}

function SummaryBox({ label, value, meta }) {
  return (
    <div className="summary-box">
      <div className="summary-box-label">{label}</div>
      <div className="summary-box-value">{value}</div>
      <div className="summary-box-meta">{meta}</div>
    </div>
  );
}

function KpiComparison({ onNavigate }) {
  const [summary, setSummary] = useState(null);
  const [run, setRun] = useState(null);
  const [vehicles, setVehicles] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let mounted = true;

    async function load() {
      try {
        setLoading(true);
        setError("");

        const [summaryData, runData, vehiclesData] = await Promise.all([
          api.getOptimizationSummary(),
          api.getLatestRun(),
          api.getVehicles(),
        ]);

        if (mounted) {
          setSummary(summaryData);
          setRun(runData);
          setVehicles(vehiclesData);
        }
      } catch (err) {
        if (mounted) {
          setError(err.message || "Unable to load optimization summary.");
        }
      } finally {
        if (mounted) {
          setLoading(false);
        }
      }
    }

    load();
    return () => {
      mounted = false;
    };
  }, []);

  const analysis = useMemo(() => {
    if (!summary) {
      return null;
    }

    const rows = summary.kpis || [];
    const byKey = Object.fromEntries(rows.map((r) => [r.key, r]));
    const improved = rows.filter((r) => r.status === "IMPROVED");
    const notImproved = rows.filter(
      (r) => r.status === "WORSENED" || r.status === "UNCHANGED"
    );

    const allocationRate = summary.baselineOrders
      ? (summary.ordersAllocated / summary.baselineOrders) * 100
      : 0;

    const coreKeys = ["total_distance", "total_cost", "planning_days", "on_time_rate"];
    const coreWorse = coreKeys.filter((k) => byKey[k]?.status === "WORSENED");

    let health = "Green";
    if (allocationRate < 100 || coreWorse.length >= 2) {
      health = "Red";
    } else if (coreWorse.length === 1 || notImproved.some((r) => r.status === "WORSENED")) {
      health = "Amber";
    }

    return { rows, byKey, improved, notImproved, allocationRate, health };
  }, [summary]);

  const header = (
    <div className="page-header">
      <div>
        <h1>Dashboard</h1>
        <p>Baseline (current planning) versus optimized plan</p>
      </div>
      {analysis && (
        <span className={`health-badge ${analysis.health.toLowerCase()}`}>
          <span className="health-dot" />
          {analysis.health === "Green"
            ? "All core KPIs improved"
            : analysis.health === "Amber"
              ? "Improved with trade-offs"
              : "Needs attention"}
        </span>
      )}
    </div>
  );

  if (loading) {
    return (
      <div className="page">
        {header}
        <div className="loading-state">Loading optimization results...</div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="page">
        {header}
        <div className="error-message">
          <strong>Unable to load the dashboard.</strong>
          <p>{error}</p>
          <p>Make sure the backend is running on port 8080.</p>
        </div>
      </div>
    );
  }

  if (!summary || !analysis) {
    return <div className="empty-state">No optimization data available.</div>;
  }

  const available = vehicles.filter(
    (v) => String(v.availability || "").toLowerCase() === "available"
  ).length;

  const p = run?.parameters || {};

  return (
    <div className="page dashboard-page">
      {header}

      <div className="section-heading">
        <h2>Plan at a Glance</h2>
        <p>
          Run {run?.runId?.slice(0, 8)} · {run?.dataSource} ·{" "}
          {run?.generatedAt ? new Date(run.generatedAt).toLocaleString() : ""}
        </p>
      </div>

      <div className="summary-box-grid">
        <SummaryBox
          label="Orders Allocated"
          value={`${formatNumber(summary.ordersAllocated)} / ${formatNumber(summary.baselineOrders)}`}
          meta={`${analysis.allocationRate.toFixed(1)}% allocation rate`}
        />
        <SummaryBox
          label="Trips Planned"
          value={formatNumber(summary.trips)}
          meta={`${formatNumber(summary.vehicleDays)} vehicle-days`}
        />
        <SummaryBox
          label="Vehicles Used"
          value={`${summary.vehiclesUsed} / ${available}`}
          meta={`${vehicles.length - available} in maintenance`}
        />
        <SummaryBox
          label="Days Needed"
          value={`${summary.planningDays} vs ${summary.baselinePlanningDays}`}
          meta="optimized vs baseline"
        />
        <SummaryBox
          label="Larger-Vehicle Carriage"
          value={formatNumber(summary.ordersCarriedByLargerVehicle)}
          meta="orders on a bigger class than required"
        />
        <SummaryBox
          label="Optimizer Runtime"
          value={`${formatNumber(summary.optimizerRuntimeMs)} ms`}
          meta={`2-opt saved ${formatNumber(summary.twoOptSavedKm, 1)} km`}
        />
      </div>

      <section className="dashboard-section">
        <div className="section-heading">
          <h2>KPI Comparison</h2>
          <p>Hover “?” for how each value is calculated.</p>
        </div>

        <div className="kpi-table-container">
          <table className="kpi-comparison-table">
            <thead>
              <tr>
                <th>KPI</th>
                <th>Direction</th>
                <th>Baseline</th>
                <th>Optimized</th>
                <th>Change</th>
                <th>Status</th>
                <th>Interpretation</th>
              </tr>
            </thead>
            <tbody>
              {analysis.rows.map((row) => {
                const s = STATUS[row.status] || STATUS.INFO;
                return (
                  <tr key={row.key}>
                    <td className="kpi-name">
                      <span className="kpi-name-content">
                        {row.name}
                        <KpiTooltip row={row} />
                      </span>
                    </td>
                    <td className="direction-cell">{DIRECTION_LABEL[row.direction]}</td>
                    <td>{formatValue(row.baseline, row.unit)}</td>
                    <td>{formatValue(row.optimized, row.unit)}</td>
                    <ChangeCell row={row} />
                    <td className="status-cell">
                      <RagIndicator status={s.rag} />
                      {s.label}
                    </td>
                    <td>{row.interpretation}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </section>

      <section className="dashboard-section">
        <div className="section-heading">
          <h2>Final Interpretation</h2>
        </div>

        <div className="interpretation-panel">
          <div className="interpretation-row">
            <RagIndicator status={analysis.allocationRate >= 100 ? "green" : "red"} />
            <span>
              The optimized plan scheduled <strong>{formatNumber(summary.ordersAllocated)}</strong> of{" "}
              <strong>{formatNumber(summary.baselineOrders)}</strong> orders (
              {analysis.allocationRate.toFixed(1)}%).
              {summary.ordersUnallocated > 0 && (
                <>
                  {" "}
                  <a href="#" onClick={(e) => { e.preventDefault(); onNavigate?.("Unallocated"); }}>
                    {summary.ordersUnallocated} unallocated
                  </a>{" "}
                  with reasons.
                </>
              )}
            </span>
          </div>

          <div className="interpretation-row">
            <RagIndicator status="green" />
            <span>
              <strong>What improved ({analysis.improved.length}):</strong>{" "}
              {analysis.improved.length === 0
                ? "none."
                : analysis.improved
                    .map((r) =>
                      r.improvementPercent != null
                        ? `${r.name} (${r.improvementPercent > 0 ? "+" : ""}${r.improvementPercent.toFixed(1)}% better)`
                        : r.name
                    )
                    .join("; ") + "."}
            </span>
          </div>

          <div className="interpretation-row">
            <RagIndicator status={analysis.notImproved.length ? "red" : "green"} />
            <span>
              <strong>What did not improve ({analysis.notImproved.length}):</strong>{" "}
              {analysis.notImproved.length === 0
                ? "every directional KPI improved."
                : analysis.notImproved
                    .map(
                      (r) =>
                        `${r.name}: ${formatValue(r.baseline, r.unit)} → ${formatValue(r.optimized, r.unit)} (${r.status.toLowerCase()})`
                    )
                    .join("; ") + "."}
              {analysis.byKey.time_utilization?.status === "WORSENED" && (
                <>
                  {" "}
                  Working-time utilisation is lower because consolidated trips need far fewer
                  vehicle-minutes; the freed capacity shows up as fewer days, not busier days.
                  The baseline figure is also inflated by per-order dispatching.
                </>
              )}
            </span>
          </div>

          <div className="interpretation-row">
            <RagIndicator status="amber" />
            <span>
              <strong>Why:</strong> orders sharing a hub and zone are consolidated into one trip
              (load utilisation {formatNumber(summary.baselineAverageLoadUtilizationPercent, 1)}% →{" "}
              {formatNumber(summary.averageLoadUtilizationPercent, 1)}%); vehicles reload and run
              several trips a day{p.multiTrip === false ? " (disabled in this run)" : ""}; orders may
              ride in a larger class when it is cheaper overall
              {p.allowVehicleUpgrade === false ? " (disabled in this run)" : ""}; and 2-opt removes
              crossing stop sequences.
            </span>
          </div>

          <div className="interpretation-note">
            <strong>Limitations.</strong> Baseline distance, time, delay and status come from the
            source fields; baseline days, cost and utilisation come from a constructed allocation,
            because the dataset has no historical vehicle assignments. Optimized times use a constant{" "}
            {p.averageSpeedKmph ?? 40} km/h and the 12-location zone matrix (no traffic, no street-level
            routing). The optimized on-time rate is a <em>planned</em> figure for each order’s
            dispatch day; it assumes the multi-day horizon is acceptable and orders have no due
            date. Waiting for windows to open adds {formatNumber(summary.totalWaitTimeMin)} min of
            idle time. The heuristic is greedy, so the result is good but not proven optimal.
          </div>
        </div>
      </section>
    </div>
  );
}

export default KpiComparison;
