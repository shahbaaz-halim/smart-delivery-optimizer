import { useEffect, useMemo, useState } from "react";
import { api } from "../services/api";

function Dashboard() {
  const [summary, setSummary] = useState(null);
  const [orders, setOrders] = useState([]);
  const [vehicles, setVehicles] = useState([]);
  const [unallocatedCount, setUnallocatedCount] = useState(0);
  const [dailyFleet, setDailyFleet] = useState([]);
  const [dailyType, setDailyType] = useState([]);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    async function loadDashboard() {
      try {
        setLoading(true);
        setError("");

        const [
          summaryData,
          ordersData,
          vehiclesData,
          unallocatedData,
          dailyFleetData,
          dailyTypeData,
        ] = await Promise.all([
          api.getOptimizationSummary(),
          api.getOrders(),
          api.getVehicles(),
          api.getUnallocatedCount(),
          api.getDailyFleetUtilization(),
          api.getDailyVehicleTypeUtilization(),
        ]);

        setSummary(summaryData);
        setOrders(ordersData);
        setVehicles(vehiclesData);
        setUnallocatedCount(unallocatedData);
        setDailyFleet(dailyFleetData || []);
        setDailyType(dailyTypeData || []);
      } catch (err) {
        setError(err.message || "Unable to load dashboard data.");
      } finally {
        setLoading(false);
      }
    }

    loadDashboard();
  }, []);

  const totalOrders = orders.length;
  const totalVehicles = vehicles.length;
  const allocatedOrders = Number(summary?.ordersAllocated || 0);
  const vehiclesUsed = Number(summary?.vehiclesUsed || 0);

  const allocationRate =
    totalOrders > 0 ? (allocatedOrders / totalOrders) * 100 : 0;

  const availableVehicles = vehicles.filter(
    (vehicle) => vehicle.availability?.toLowerCase() === "available"
  ).length;

  const fleetUsageRate =
    availableVehicles > 0
      ? (vehiclesUsed / availableVehicles) * 100
      : 0;

  const maxDailyUtilization = useMemo(
    () =>
      dailyFleet.reduce(
        (max, item) => Math.max(max, Number(item.utilizationPercent || 0)),
        0
      ),
    [dailyFleet]
  );

  const formatNumber = (value, decimals = 1) =>
    Number(value || 0).toLocaleString("en-IN", {
      minimumFractionDigits: decimals,
      maximumFractionDigits: decimals,
    });

  const formatCurrency = (value) =>
    `₹${Number(value || 0).toLocaleString("en-IN", {
      maximumFractionDigits: 0,
    })}`;

  if (loading) {
    return (
      <div className="page-state">
        <h1>Dashboard</h1>
        <p>Loading optimization data...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div>
        <h1>Dashboard</h1>
        <div className="error-message">
          <strong>Unable to load dashboard data.</strong>
          <p>{error}</p>
          <p>Make sure the Spring Boot backend is running on port 8080.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="dashboard">
      <div className="page-header">
        <div>
          <h1>Operations Dashboard</h1>
          <p>Delivery allocation, fleet utilization and optimization results.</p>
        </div>
        <div className="header-status">
          <span className="status-dot" />
          Optimization data live
        </div>
      </div>

      <div className="kpi-grid">
        <div className="kpi-card">
          <div className="kpi-label">Total Orders</div>
          <div className="kpi-value">{totalOrders.toLocaleString("en-IN")}</div>
          <div className="kpi-description">Orders in the supplied dataset</div>
        </div>

        <div className="kpi-card">
          <div className="kpi-label">Orders Allocated</div>
          <div className="kpi-value">{allocatedOrders.toLocaleString("en-IN")}</div>
          <div className="kpi-description">{allocationRate.toFixed(1)}% allocation rate</div>
        </div>

        <div className="kpi-card">
          <div className="kpi-label">Vehicles Used</div>
          <div className="kpi-value">{vehiclesUsed}</div>
          <div className="kpi-description">
            {fleetUsageRate.toFixed(1)}% of available fleet
          </div>
        </div>

        <div className="kpi-card">
          <div className="kpi-label">Unallocated Orders</div>
          <div className="kpi-value">{unallocatedCount}</div>
          <div className="kpi-description">Orders outside current constraints</div>
        </div>

        <div className="kpi-card">
          <div className="kpi-label">Optimized Distance</div>
          <div className="kpi-value">{formatNumber(summary?.totalDistanceKm)} km</div>
          <div className="kpi-description">Total planned route distance</div>
        </div>

        <div className="kpi-card">
          <div className="kpi-label">Optimized Time</div>
          <div className="kpi-value">{formatNumber(summary?.totalTimeMin)} min</div>
          <div className="kpi-description">Travel plus service time</div>
        </div>

        <div className="kpi-card">
          <div className="kpi-label">Optimized Cost</div>
          <div className="kpi-value">{formatCurrency(summary?.totalCostInr)}</div>
          <div className="kpi-description">Estimated route operating cost</div>
        </div>

        <div className="kpi-card">
          <div className="kpi-label">Planning Horizon</div>
          <div className="kpi-value">{summary?.planningDays || 0} days</div>
          <div className="kpi-description">Optimization planning horizon</div>
        </div>
      </div>

      <div className="dashboard-grid">
        <section className="dashboard-panel dashboard-wide-panel">
          <div className="panel-header">
            <div>
              <h2>Daily Fleet Utilization</h2>
              <p>Vehicles used against the available fleet by planning day.</p>
            </div>
            <strong>{formatNumber(maxDailyUtilization)}% peak</strong>
          </div>

          <div className="daily-chart">
            {dailyFleet.length === 0 ? (
              <div className="chart-empty">No daily utilization data available.</div>
            ) : (
              dailyFleet.map((item) => {
                const utilization = Number(item.utilizationPercent || 0);
                return (
                  <div className="daily-bar-item" key={item.planningDay}>
                    <div className="daily-bar-value">{utilization.toFixed(1)}%</div>
                    <div className="daily-bar-track">
                      <div
                        className="daily-bar-fill"
                        style={{ width: `${Math.min(100, utilization)}%` }}
                      />
                    </div>
                    <div className="daily-bar-label">
                      Day {item.planningDay}
                    </div>
                    <div className="daily-bar-meta">
                      {item.vehiclesUsed} / {item.availableVehicles} vehicles
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </section>

        <section className="dashboard-panel">
          <div className="panel-header">
            <h2>Optimization Summary</h2>
          </div>

          <div className="summary-row">
            <span>Orders Allocated</span>
            <strong>{allocatedOrders.toLocaleString("en-IN")}</strong>
          </div>
          <div className="summary-row">
            <span>Total Weight</span>
            <strong>{formatNumber(summary?.totalWeightKg)} kg</strong>
          </div>
          <div className="summary-row">
            <span>Total Distance</span>
            <strong>{formatNumber(summary?.totalDistanceKm)} km</strong>
          </div>
          <div className="summary-row">
            <span>Total Time</span>
            <strong>{formatNumber(summary?.totalTimeMin)} min</strong>
          </div>
          <div className="summary-row">
            <span>Total Cost</span>
            <strong>{formatCurrency(summary?.totalCostInr)}</strong>
          </div>
        </section>

        <section className="dashboard-panel">
          <div className="panel-header">
            <h2>Vehicle-Type Utilization</h2>
            <p>Daily resource usage by vehicle type.</p>
          </div>

          <div className="type-utilization-list">
            {dailyType.length === 0 ? (
              <div className="chart-empty">No vehicle-type utilization data.</div>
            ) : (
              dailyType.slice(0, 12).map((item, index) => {
                const utilization = Number(item.utilizationPercent || 0);
                return (
                  <div className="type-utilization-row" key={`${item.planningDay}-${item.vehicleType}-${index}`}>
                    <div>
                      <strong>{item.vehicleType}</strong>
                      <span>Day {item.planningDay}</span>
                    </div>
                    <div className="type-utilization-meter">
                      <div
                        className="type-utilization-fill"
                        style={{ width: `${Math.min(100, utilization)}%` }}
                      />
                    </div>
                    <strong>{utilization.toFixed(1)}%</strong>
                  </div>
                );
              })
            )}
          </div>
        </section>

        <section className="dashboard-panel">
          <div className="panel-header">
            <h2>System Overview</h2>
          </div>

          <div className="summary-row">
            <span>Total Fleet</span>
            <strong>{totalVehicles}</strong>
          </div>
          <div className="summary-row">
            <span>Available Vehicles</span>
            <strong>{availableVehicles}</strong>
          </div>
          <div className="summary-row">
            <span>Allocation Rate</span>
            <strong>{allocationRate.toFixed(1)}%</strong>
          </div>
          <div className="summary-row">
            <span>Vehicles Utilized</span>
            <strong>{vehiclesUsed}</strong>
          </div>
          <div className="summary-row">
            <span>Planning Days</span>
            <strong>{summary?.planningDays || 0}</strong>
          </div>
        </section>
      </div>
    </div>
  );
}

export default Dashboard;
