import { useEffect, useMemo, useState } from "react";
import { api } from "../services/api";

function Trips() {
  const [routes, setRoutes] = useState([]);
  const [selectedTrip, setSelectedTrip] = useState(null);

  const [search, setSearch] = useState("");
  const [dayFilter, setDayFilter] = useState("All");
  const [hubFilter, setHubFilter] = useState("All");
  const [typeFilter, setTypeFilter] = useState("All");

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    async function loadTrips() {
      try {
        setLoading(true);
        setError("");
        setRoutes(await api.getRoutes());
      } catch (err) {
        setError(err.message || "Unable to load trip data.");
      } finally {
        setLoading(false);
      }
    }

    loadTrips();
  }, []);

  const filteredRoutes = useMemo(() => {
    const searchValue = search.trim().toLowerCase();

    return routes.filter((route) => {
      const vehicle = route.vehicle;
      if (!vehicle) return false;

      const matchesSearch =
        !searchValue ||
        vehicle.vehicleId?.toLowerCase().includes(searchValue) ||
        vehicle.hub?.toLowerCase().includes(searchValue) ||
        vehicle.vehicleType?.toLowerCase().includes(searchValue);

      const matchesDay =
        dayFilter === "All" ||
        Number(route.planningDay) === Number(dayFilter);

      const matchesHub = hubFilter === "All" || vehicle.hub === hubFilter;
      const matchesType = typeFilter === "All" || vehicle.vehicleType === typeFilter;

      return matchesSearch && matchesDay && matchesHub && matchesType;
    });
  }, [routes, search, dayFilter, hubFilter, typeFilter]);

  const planningDays = [...new Set(routes.map((route) => route.planningDay))]
    .filter((day) => day !== null && day !== undefined)
    .sort((a, b) => a - b);

  const hubs = [...new Set(routes.map((route) => route.vehicle?.hub))]
    .filter(Boolean)
    .sort();

  const vehicleTypes = [...new Set(routes.map((route) => route.vehicle?.vehicleType))]
    .filter(Boolean)
    .sort();

  const totalOrders = routes.reduce(
    (total, route) => total + (route.orders?.length || 0),
    0
  );

  const totalDistance = routes.reduce(
    (total, route) => total + Number(route.totalDistanceKm || 0),
    0
  );

  const totalTime = routes.reduce(
    (total, route) => total + Number(route.totalTimeMin || 0),
    0
  );

  const totalCost = routes.reduce(
    (total, route) => total + Number(route.totalCostInr || 0),
    0
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

  const getWeightUtilization = (route) => {
    const capacity = Number(route.vehicle?.capacityKg || 0);
    return capacity ? (Number(route.totalWeightKg || 0) / capacity) * 100 : 0;
  };

  const getTimeUtilization = (route) => {
    const maxTime = Number(route.vehicle?.maxWorkingMinutes || 0);
    return maxTime ? (Number(route.totalTimeMin || 0) / maxTime) * 100 : 0;
  };

  if (loading) {
    return (
      <div className="page-state">
        <h1>Trip / Dispatch Plan</h1>
        <p>Loading optimized trips...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div>
        <h1>Trip / Dispatch Plan</h1>
        <div className="error-message">
          <strong>Unable to load trip data.</strong>
          <p>{error}</p>
        </div>
      </div>
    );
  }

  return (
    <div className="trips-page">
      <div className="page-header">
        <div>
          <h1>Trip / Dispatch Plan</h1>
          <p>Optimized vehicle trips across the planning horizon.</p>
        </div>
      </div>

      <div className="trip-summary-grid">
        <div className="trip-summary-card">
          <span>Optimized Trips</span>
          <strong>{routes.length}</strong>
        </div>
        <div className="trip-summary-card">
          <span>Orders Dispatched</span>
          <strong>{totalOrders.toLocaleString("en-IN")}</strong>
        </div>
        <div className="trip-summary-card">
          <span>Total Distance</span>
          <strong>{formatNumber(totalDistance)} km</strong>
        </div>
        <div className="trip-summary-card">
          <span>Total Cost</span>
          <strong>{formatCurrency(totalCost)}</strong>
        </div>
      </div>

      <section className="trips-main-panel">
        <div className="trips-toolbar">
          <div className="search-box">
            <input
              type="text"
              placeholder="Search vehicle, hub or type..."
              value={search}
              onChange={(event) => setSearch(event.target.value)}
            />
          </div>

          <select value={dayFilter} onChange={(event) => setDayFilter(event.target.value)}>
            <option value="All">All Planning Days</option>
            {planningDays.map((day) => (
              <option key={day} value={day}>Day {day}</option>
            ))}
          </select>

          <select value={hubFilter} onChange={(event) => setHubFilter(event.target.value)}>
            <option value="All">All Hubs</option>
            {hubs.map((hub) => <option key={hub} value={hub}>{hub}</option>)}
          </select>

          <select value={typeFilter} onChange={(event) => setTypeFilter(event.target.value)}>
            <option value="All">All Vehicle Types</option>
            {vehicleTypes.map((type) => <option key={type} value={type}>{type}</option>)}
          </select>
        </div>

        <div className="trips-table-header">
          <span>Showing {filteredRoutes.length} of {routes.length} trips</span>
          <span>{formatNumber(totalTime)} min total optimized time</span>
        </div>

        <div className="table-container">
          <table className="data-table">
            <thead>
              <tr>
                <th>Trip</th>
                <th>Day</th>
                <th>Vehicle</th>
                <th>Hub</th>
                <th>Type</th>
                <th>Orders</th>
                <th>Distance</th>
                <th>Weight</th>
                <th>Time</th>
                <th>Cost</th>
                <th>Capacity</th>
              </tr>
            </thead>
            <tbody>
              {filteredRoutes.map((route, index) => {
                const vehicle = route.vehicle;
                const weightUtilization = getWeightUtilization(route);

                return (
                  <tr
                    key={`${route.planningDay}-${vehicle.vehicleId}-${index}`}
                    className={selectedTrip === route ? "selected-row" : ""}
                    onClick={() => setSelectedTrip(route)}
                  >
                    <td className="order-id-cell">
                      TRIP-{String(index + 1).padStart(3, "0")}
                    </td>
                    <td>
                      Day {route.planningDay} · T{route.tripNumber ?? 1}
                      {route.startTime && (
                        <div className="cell-sub">{route.startTime}–{route.endTime}</div>
                      )}
                    </td>
                    <td>{vehicle.vehicleId}</td>
                    <td>{vehicle.hub}</td>
                    <td>{vehicle.vehicleType}</td>
                    <td><strong>{route.orders?.length || 0}</strong></td>
                    <td>{formatNumber(route.totalDistanceKm)} km</td>
                    <td>{formatNumber(route.totalWeightKg)} kg</td>
                    <td>{formatNumber(route.totalTimeMin)} min</td>
                    <td>{formatCurrency(route.totalCostInr)}</td>
                    <td>
                      <div className="trip-utilization">
                        <span>{formatNumber(weightUtilization)}%</span>
                        <div className="mini-utilization-bar">
                          <div
                            className="mini-utilization-fill"
                            style={{ width: `${Math.min(100, weightUtilization)}%` }}
                          />
                        </div>
                      </div>
                    </td>
                  </tr>
                );
              })}

              {filteredRoutes.length === 0 && (
                <tr>
                  <td colSpan="11" className="empty-table">
                    No trips match the selected filters.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>

      <aside className={`trip-detail-panel ${selectedTrip ? "trip-detail-open" : ""}`}>
        {!selectedTrip ? (
          <div className="empty-detail">
            <div className="empty-detail-icon">↗</div>
            <h2>Select a Trip</h2>
            <p>Click a trip above to view its dispatch sequence and resource utilization.</p>
          </div>
        ) : (
          <>
            <div className="detail-header">
              <div>
                <div className="detail-label">DISPATCH TRIP</div>
                <h2>{selectedTrip.vehicle.vehicleId}</h2>
              </div>
              <button className="close-detail" onClick={() => setSelectedTrip(null)}>×</button>
            </div>

            <div className="detail-status-row">
              <span className="assignment-badge assigned">
                Day {selectedTrip.planningDay} · Trip {selectedTrip.tripNumber ?? 1}
              </span>
              {selectedTrip.startTime && (
                <span className="status-badge">
                  {selectedTrip.startTime}–{selectedTrip.endTime}
                </span>
              )}
              <span className="status-badge">{selectedTrip.vehicle.vehicleType}</span>
              <span className="status-badge">{selectedTrip.vehicle.hub}</span>
            </div>

            <div className="detail-section">
              <h3>Trip Summary</h3>
              <div className="detail-grid">
                <div><span>Orders</span><strong>{selectedTrip.orders?.length || 0}</strong></div>
                <div><span>Distance</span><strong>{formatNumber(selectedTrip.totalDistanceKm)} km</strong></div>
                <div><span>Total Weight</span><strong>{formatNumber(selectedTrip.totalWeightKg)} kg</strong></div>
                <div><span>Total Time</span><strong>{formatNumber(selectedTrip.totalTimeMin)} min</strong></div>
                <div><span>Trip Cost</span><strong>{formatCurrency(selectedTrip.totalCostInr)}</strong></div>
                <div><span>Cost / Km</span><strong>{formatCurrency(selectedTrip.vehicle.costPerKmInr)}</strong></div>
              </div>
            </div>

            <div className="detail-section">
              <h3>Resource Utilization</h3>

              <div className="utilization-block">
                <div className="utilization-header">
                  <span>Capacity Utilization</span>
                  <strong>{formatNumber(getWeightUtilization(selectedTrip))}%</strong>
                </div>
                <div className="utilization-bar">
                  <div
                    className="utilization-fill"
                    style={{ width: `${Math.min(100, getWeightUtilization(selectedTrip))}%` }}
                  />
                </div>
                <div className="utilization-caption">
                  {formatNumber(selectedTrip.totalWeightKg)} kg / {formatNumber(selectedTrip.vehicle.capacityKg)} kg
                </div>
              </div>

              <div className="utilization-block">
                <div className="utilization-header">
                  <span>Working Time Utilization</span>
                  <strong>{formatNumber(getTimeUtilization(selectedTrip))}%</strong>
                </div>
                <div className="utilization-bar">
                  <div
                    className="utilization-fill"
                    style={{ width: `${Math.min(100, getTimeUtilization(selectedTrip))}%` }}
                  />
                </div>
                <div className="utilization-caption">
                  {formatNumber(selectedTrip.totalTimeMin)} min / {selectedTrip.vehicle.maxWorkingMinutes} min
                </div>
              </div>
            </div>

            <div className="detail-section">
              <h3>Delivery Sequence</h3>
              <div className="vehicle-orders-list">
                {selectedTrip.orders?.map((order, index) => {
                  const stop = selectedTrip.stops?.[index];
                  return (
                  <div className="vehicle-order-item" key={order.orderId}>
                    <div className="vehicle-order-sequence">#{index + 1}</div>
                    <div className="vehicle-order-info">
                      <strong>{order.orderId}</strong>
                      <span>{order.originHub} → {order.destinationZone}</span>
                      <span>{order.deliveryWindow} • {order.priority}</span>
                      {stop && (
                        <span>
                          Arrive {stop.arrivalTime}
                          {stop.waitMin > 0.5 && ` · wait ${formatNumber(stop.waitMin, 0)} min`}
                          {" · "}service {stop.serviceStartTime}
                          {stop.onTime ? " ✓" : ` · late ${formatNumber(stop.lateMin, 0)} min`}
                        </span>
                      )}
                    </div>
                    <div className="vehicle-order-weight">
                      {formatNumber(order.packageWeightKg)} kg
                    </div>
                  </div>
                  );
                })}
              </div>
            </div>
          </>
        )}
      </aside>
    </div>
  );
}

export default Trips;
