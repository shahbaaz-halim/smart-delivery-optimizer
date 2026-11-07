import { useEffect, useMemo, useState } from "react";
import { api } from "../services/api";

function Vehicles() {
  const [vehicles, setVehicles] = useState([]);
  const [routes, setRoutes] = useState([]);

  const [search, setSearch] = useState("");
  const [hubFilter, setHubFilter] = useState("All");
  const [typeFilter, setTypeFilter] = useState("All");
  const [availabilityFilter, setAvailabilityFilter] = useState("All");
  const [usageFilter, setUsageFilter] = useState("All");

  const [selectedVehicle, setSelectedVehicle] = useState(null);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    async function loadVehicles() {
      try {
        setLoading(true);
        setError("");
        const [vehiclesData, routesData] = await Promise.all([
          api.getVehicles(),
          api.getRoutes(),
        ]);
        setVehicles(vehiclesData);
        setRoutes(routesData);
      } catch (err) {
        setError(err.message || "Unable to load vehicle data.");
      } finally {
        setLoading(false);
      }
    }

    loadVehicles();
  }, []);

  const routeMap = useMemo(() => {
    const map = new Map();

    routes.forEach((route) => {
      const vehicleId = route.vehicle?.vehicleId;
      if (!vehicleId) return;

      if (!map.has(vehicleId)) map.set(vehicleId, []);
      map.get(vehicleId).push(route);
    });

    return map;
  }, [routes]);

  const filteredVehicles = useMemo(() => {
    const searchValue = search.trim().toLowerCase();

    return vehicles.filter((vehicle) => {
      const vehicleRoutes = routeMap.get(vehicle.vehicleId) || [];
      const isUsed = vehicleRoutes.some((route) => route.orders?.length);

      const matchesSearch =
        !searchValue ||
        vehicle.vehicleId?.toLowerCase().includes(searchValue) ||
        vehicle.hub?.toLowerCase().includes(searchValue) ||
        vehicle.vehicleType?.toLowerCase().includes(searchValue);

      const matchesHub = hubFilter === "All" || vehicle.hub === hubFilter;
      const matchesType = typeFilter === "All" || vehicle.vehicleType === typeFilter;
      const matchesAvailability =
        availabilityFilter === "All" ||
        vehicle.availability === availabilityFilter;

      const matchesUsage =
        usageFilter === "All" ||
        (usageFilter === "Used" && isUsed) ||
        (usageFilter === "Unused" && !isUsed);

      return (
        matchesSearch &&
        matchesHub &&
        matchesType &&
        matchesAvailability &&
        matchesUsage
      );
    });
  }, [
    vehicles,
    routeMap,
    search,
    hubFilter,
    typeFilter,
    availabilityFilter,
    usageFilter,
  ]);

  const hubs = [...new Set(vehicles.map((vehicle) => vehicle.hub))]
    .filter(Boolean)
    .sort();

  const vehicleTypes = [...new Set(vehicles.map((vehicle) => vehicle.vehicleType))]
    .filter(Boolean)
    .sort();

  const availabilityValues = [...new Set(vehicles.map((vehicle) => vehicle.availability))]
    .filter(Boolean)
    .sort();

  const usedCount = vehicles.filter((vehicle) =>
    (routeMap.get(vehicle.vehicleId) || []).some((route) => route.orders?.length)
  ).length;

  const availableCount = vehicles.filter(
    (vehicle) => vehicle.availability?.toLowerCase() === "available"
  ).length;

  const resetFilters = () => {
    setSearch("");
    setHubFilter("All");
    setTypeFilter("All");
    setAvailabilityFilter("All");
    setUsageFilter("All");
  };

  const formatNumber = (value, decimals = 1) =>
    Number(value || 0).toLocaleString("en-IN", {
      minimumFractionDigits: decimals,
      maximumFractionDigits: decimals,
    });

  const formatCurrency = (value) =>
    `₹${Number(value || 0).toLocaleString("en-IN", {
      maximumFractionDigits: 0,
    })}`;

  const getVehicleStats = (vehicle) => {
    const vehicleRoutes = routeMap.get(vehicle.vehicleId) || [];

    const orders = vehicleRoutes.reduce(
      (sum, route) => sum + (route.orders?.length || 0),
      0
    );

    const totalWeight = vehicleRoutes.reduce(
      (sum, route) => sum + Number(route.totalWeightKg || 0),
      0
    );

    const totalDistance = vehicleRoutes.reduce(
      (sum, route) => sum + Number(route.totalDistanceKm || 0),
      0
    );

    const totalTime = vehicleRoutes.reduce(
      (sum, route) => sum + Number(route.totalTimeMin || 0),
      0
    );

    const totalCost = vehicleRoutes.reduce(
      (sum, route) => sum + Number(route.totalCostInr || 0),
      0
    );

    // A vehicle can run several trips per day, so count distinct days.
    const usedDays = new Set(
      vehicleRoutes
        .filter((route) => route.orders?.length)
        .map((route) => route.planningDay)
    ).size;

    return {
      routes: vehicleRoutes,
      orders,
      totalWeight,
      totalDistance,
      totalTime,
      totalCost,
      usedDays,
      weightUtilization:
        vehicle.capacityKg > 0
          ? (totalWeight / (vehicle.capacityKg * Math.max(1, usedDays))) * 100
          : 0,
      timeUtilization:
        vehicle.maxWorkingMinutes > 0
          ? (totalTime / (vehicle.maxWorkingMinutes * Math.max(1, usedDays))) * 100
          : 0,
    };
  };

  if (loading) {
    return (
      <div className="page-state">
        <h1>Vehicle Explorer</h1>
        <p>Loading vehicle data...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div>
        <h1>Vehicle Explorer</h1>
        <div className="error-message">
          <strong>Unable to load vehicle data.</strong>
          <p>{error}</p>
        </div>
      </div>
    );
  }

  const selectedStats = selectedVehicle
    ? getVehicleStats(selectedVehicle)
    : null;

  return (
    <div className="vehicles-page">
      <div className="page-header">
        <div>
          <h1>Vehicle Explorer</h1>
          <p>Explore fleet capacity, utilization and multi-day optimization assignments.</p>
        </div>
      </div>

      <div className="vehicle-summary-grid">
        <div className="vehicle-summary-card">
          <span>Total Fleet</span>
          <strong>{vehicles.length}</strong>
        </div>
        <div className="vehicle-summary-card">
          <span>Available</span>
          <strong>{availableCount}</strong>
        </div>
        <div className="vehicle-summary-card">
          <span>Used</span>
          <strong>{usedCount}</strong>
        </div>
        <div className="vehicle-summary-card">
          <span>Unused</span>
          <strong>{vehicles.length - usedCount}</strong>
        </div>
      </div>

      <div className="vehicle-layout">
        <section className="vehicles-main-panel">
          <div className="vehicles-toolbar">
            <div className="search-box">
              <input
                type="text"
                placeholder="Search vehicle, hub or type..."
                value={search}
                onChange={(event) => setSearch(event.target.value)}
              />
            </div>

            <select value={hubFilter} onChange={(event) => setHubFilter(event.target.value)}>
              <option value="All">All Hubs</option>
              {hubs.map((hub) => <option key={hub} value={hub}>{hub}</option>)}
            </select>

            <select value={typeFilter} onChange={(event) => setTypeFilter(event.target.value)}>
              <option value="All">All Vehicle Types</option>
              {vehicleTypes.map((type) => <option key={type} value={type}>{type}</option>)}
            </select>

            <select value={availabilityFilter} onChange={(event) => setAvailabilityFilter(event.target.value)}>
              <option value="All">All Availability</option>
              {availabilityValues.map((value) => <option key={value} value={value}>{value}</option>)}
            </select>

            <select value={usageFilter} onChange={(event) => setUsageFilter(event.target.value)}>
              <option value="All">All Usage</option>
              <option value="Used">Used</option>
              <option value="Unused">Unused</option>
            </select>

            <button className="reset-button" onClick={resetFilters}>Reset</button>
          </div>

          <div className="vehicles-table-header">
            Showing {filteredVehicles.length} of {vehicles.length} vehicles
          </div>

          <div className="table-container">
            <table className="data-table vehicle-table">
              <thead>
                <tr>
                  <th>Vehicle</th>
                  <th>Hub</th>
                  <th>Type</th>
                  <th>Capacity</th>
                  <th>Availability</th>
                  <th>Days Used</th>
                  <th>Orders</th>
                  <th>Avg Weight Util.</th>
                  <th>Avg Time Util.</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {filteredVehicles.map((vehicle) => {
                  const stats = getVehicleStats(vehicle);
                  const isUsed = stats.orders > 0;

                  return (
                    <tr
                      key={vehicle.vehicleId}
                      className={
                        selectedVehicle?.vehicleId === vehicle.vehicleId
                          ? "selected-row"
                          : ""
                      }
                      onClick={() => setSelectedVehicle(vehicle)}
                    >
                      <td className="order-id-cell">{vehicle.vehicleId}</td>
                      <td>{vehicle.hub}</td>
                      <td>{vehicle.vehicleType}</td>
                      <td>{formatNumber(vehicle.capacityKg)} kg</td>
                      <td><span className="status-badge">{vehicle.availability}</span></td>
                      <td>{stats.usedDays}</td>
                      <td><strong>{stats.orders}</strong></td>
                      <td>{isUsed ? `${formatNumber(stats.weightUtilization)}%` : "-"}</td>
                      <td>{isUsed ? `${formatNumber(stats.timeUtilization)}%` : "-"}</td>
                      <td>
                        {isUsed ? (
                          <span className="assignment-badge assigned">In Use</span>
                        ) : (
                          <span className="assignment-badge unassigned">Unused</span>
                        )}
                      </td>
                    </tr>
                  );
                })}

                {filteredVehicles.length === 0 && (
                  <tr>
                    <td colSpan="10" className="empty-table">
                      No vehicles match the selected filters.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </section>

        <aside className="vehicle-detail-panel">
          {!selectedVehicle ? (
            <div className="empty-detail">
              <div className="empty-detail-icon">🚚</div>
              <h2>Select a Vehicle</h2>
              <p>Click a vehicle to view its multi-day fleet utilization and assigned trips.</p>
            </div>
          ) : (
            <>
              <div className="detail-header">
                <div>
                  <div className="detail-label">VEHICLE</div>
                  <h2>{selectedVehicle.vehicleId}</h2>
                </div>
                <button className="close-detail" onClick={() => setSelectedVehicle(null)}>×</button>
              </div>

              <div className="detail-status-row">
                <span className="assignment-badge assigned">{selectedVehicle.vehicleType}</span>
                <span className="status-badge">{selectedVehicle.availability}</span>
              </div>

              <div className="detail-section">
                <h3>Vehicle Details</h3>
                <div className="detail-grid">
                  <div><span>Vehicle ID</span><strong>{selectedVehicle.vehicleId}</strong></div>
                  <div><span>Hub</span><strong>{selectedVehicle.hub}</strong></div>
                  <div><span>Capacity</span><strong>{formatNumber(selectedVehicle.capacityKg)} kg</strong></div>
                  <div><span>Cost / Km</span><strong>{formatCurrency(selectedVehicle.costPerKmInr)}</strong></div>
                  <div><span>Max Working Time</span><strong>{selectedVehicle.maxWorkingMinutes} min</strong></div>
                  <div><span>Planning Days Used</span><strong>{selectedStats.usedDays}</strong></div>
                </div>
              </div>

              <div className="detail-section">
                <h3>Planning-Horizon Summary</h3>
                <div className="detail-grid">
                  <div><span>Orders</span><strong>{selectedStats.orders}</strong></div>
                  <div><span>Distance</span><strong>{formatNumber(selectedStats.totalDistance)} km</strong></div>
                  <div><span>Time</span><strong>{formatNumber(selectedStats.totalTime)} min</strong></div>
                  <div><span>Cost</span><strong>{formatCurrency(selectedStats.totalCost)}</strong></div>
                </div>
              </div>

              <div className="detail-section">
                <h3>Trips by Planning Day</h3>
                <div className="vehicle-orders-list">
                  {selectedStats.routes.length === 0 ? (
                    <div className="unallocated-detail">
                      <strong>Vehicle not used</strong>
                      <p>No orders are assigned to this vehicle.</p>
                    </div>
                  ) : (
                    selectedStats.routes.map((route) => (
                      <div
                        className="vehicle-order-item"
                        key={`day-${route.planningDay}-trip-${route.tripNumber ?? 1}`}
                      >
                        <div className="vehicle-order-sequence">
                          D{route.planningDay}
                        </div>
                        <div className="vehicle-order-info">
                          <strong>
                            Trip {route.tripNumber ?? 1} · {route.orders?.length || 0} orders
                            {route.startTime && ` · ${route.startTime}–${route.endTime}`}
                          </strong>
                          <span>{formatNumber(route.totalDistanceKm)} km • {formatNumber(route.totalTimeMin)} min</span>
                          <span>{formatCurrency(route.totalCostInr)}</span>
                        </div>
                        <div className="vehicle-order-weight">
                          {formatNumber(route.totalWeightKg)} kg
                        </div>
                      </div>
                    ))
                  )}
                </div>
              </div>
            </>
          )}
        </aside>
      </div>
    </div>
  );
}

export default Vehicles;
