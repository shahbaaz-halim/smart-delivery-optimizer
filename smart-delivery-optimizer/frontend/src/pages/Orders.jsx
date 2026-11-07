import { useEffect, useMemo, useState } from "react";
import { api } from "../services/api";

const PAGE_SIZE = 25;

function Orders() {
  const [orders, setOrders] = useState([]);
  const [routes, setRoutes] = useState([]);

  const [search, setSearch] = useState("");
  const [hubFilter, setHubFilter] = useState("All");
  const [priorityFilter, setPriorityFilter] = useState("All");
  const [vehicleFilter, setVehicleFilter] = useState("All");
  const [statusFilter, setStatusFilter] = useState("All");

  const [selectedOrder, setSelectedOrder] = useState(null);
  const [currentPage, setCurrentPage] = useState(1);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    async function loadOrders() {
      try {
        setLoading(true);
        setError("");

        const [ordersData, routesData] = await Promise.all([
          api.getOrders(),
          api.getRoutes(),
        ]);

        setOrders(ordersData);
        setRoutes(routesData);
      } catch (err) {
        setError(err.message);
      } finally {
        setLoading(false);
      }
    }

    loadOrders();
  }, []);

  const assignmentMap = useMemo(() => {
    const map = new Map();

    routes.forEach((route) => {
      const vehicle = route.vehicle;

      if (!vehicle || !route.orders) {
        return;
      }

      route.orders.forEach((order, index) => {
        map.set(order.orderId, {
          vehicleId: vehicle.vehicleId,
          sequence: index + 1,
          route: route,
          stop: route.stops?.[index],
        });
      });
    });

    return map;
  }, [routes]);

  const filteredOrders = useMemo(() => {
    const searchValue = search.trim().toLowerCase();

    return orders.filter((order) => {
      const matchesSearch =
        !searchValue ||
        order.orderId?.toLowerCase().includes(searchValue) ||
        order.originHub?.toLowerCase().includes(searchValue) ||
        order.destinationZone?.toLowerCase().includes(searchValue);

      const matchesHub =
        hubFilter === "All" ||
        order.originHub === hubFilter;

      const matchesPriority =
        priorityFilter === "All" ||
        order.priority === priorityFilter;

      const matchesVehicle =
        vehicleFilter === "All" ||
        order.requiredVehicleType === vehicleFilter;

      const matchesStatus =
        statusFilter === "All" ||
        order.deliveryStatus === statusFilter;

      return (
        matchesSearch &&
        matchesHub &&
        matchesPriority &&
        matchesVehicle &&
        matchesStatus
      );
    });
  }, [
    orders,
    search,
    hubFilter,
    priorityFilter,
    vehicleFilter,
    statusFilter,
  ]);

  const totalPages = Math.max(
    1,
    Math.ceil(filteredOrders.length / PAGE_SIZE)
  );

  const safeCurrentPage = Math.min(currentPage, totalPages);

  const paginatedOrders = filteredOrders.slice(
    (safeCurrentPage - 1) * PAGE_SIZE,
    safeCurrentPage * PAGE_SIZE
  );

  const hubs = [...new Set(orders.map((order) => order.originHub))]
    .filter(Boolean)
    .sort();

  const priorities = [...new Set(orders.map((order) => order.priority))]
    .filter(Boolean)
    .sort();

  const vehicleTypes = [
    ...new Set(
      orders.map((order) => order.requiredVehicleType)
    ),
  ]
    .filter(Boolean)
    .sort();

  const statuses = [
    ...new Set(
      orders.map((order) => order.deliveryStatus)
    ),
  ]
    .filter(Boolean)
    .sort();

  function resetFilters() {
    setSearch("");
    setHubFilter("All");
    setPriorityFilter("All");
    setVehicleFilter("All");
    setStatusFilter("All");
    setCurrentPage(1);
  }

  function handleFilterChange(setter, value) {
    setter(value);
    setCurrentPage(1);
  }

  function formatNumber(value, decimals = 1) {
    if (value === null || value === undefined) {
      return "-";
    }

    return Number(value).toFixed(decimals);
  }

  function formatCurrency(value) {
    if (value === null || value === undefined) {
      return "-";
    }

    return `₹${Number(value).toLocaleString("en-IN", {
      maximumFractionDigits: 0,
    })}`;
  }

  if (loading) {
    return (
      <div>
        <h1>Order Explorer</h1>
        <p>Loading delivery orders...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div>
        <h1>Order Explorer</h1>

        <div className="error-message">
          <strong>Unable to load order data.</strong>
          <p>{error}</p>
        </div>
      </div>
    );
  }

  const selectedAssignment = selectedOrder
    ? assignmentMap.get(selectedOrder.orderId)
    : null;

  return (
    <div className="orders-page">
      <div className="page-header">
        <div>
          <h1>Order Explorer</h1>
          <p>
            Explore delivery orders, operational constraints and
            optimization assignments.
          </p>
        </div>
      </div>

      <div className="order-layout">
        <section className="orders-main-panel">
          <div className="orders-toolbar">
            <div className="search-box">
              <input
                type="text"
                placeholder="Search order, hub or destination..."
                value={search}
                onChange={(event) =>
                  handleFilterChange(
                    setSearch,
                    event.target.value
                  )
                }
              />
            </div>

            <select
              value={hubFilter}
              onChange={(event) =>
                handleFilterChange(
                  setHubFilter,
                  event.target.value
                )
              }
            >
              <option value="All">All Hubs</option>

              {hubs.map((hub) => (
                <option key={hub} value={hub}>
                  {hub}
                </option>
              ))}
            </select>

            <select
              value={priorityFilter}
              onChange={(event) =>
                handleFilterChange(
                  setPriorityFilter,
                  event.target.value
                )
              }
            >
              <option value="All">All Priorities</option>

              {priorities.map((priority) => (
                <option key={priority} value={priority}>
                  {priority}
                </option>
              ))}
            </select>

            <select
              value={vehicleFilter}
              onChange={(event) =>
                handleFilterChange(
                  setVehicleFilter,
                  event.target.value
                )
              }
            >
              <option value="All">All Vehicle Types</option>

              {vehicleTypes.map((vehicleType) => (
                <option
                  key={vehicleType}
                  value={vehicleType}
                >
                  {vehicleType}
                </option>
              ))}
            </select>

            <select
              value={statusFilter}
              onChange={(event) =>
                handleFilterChange(
                  setStatusFilter,
                  event.target.value
                )
              }
            >
              <option value="All">All Statuses</option>

              {statuses.map((status) => (
                <option key={status} value={status}>
                  {status}
                </option>
              ))}
            </select>

            <button
              className="reset-button"
              onClick={resetFilters}
            >
              Reset
            </button>
          </div>

          <div className="orders-table-header">
            <span>
              Showing {filteredOrders.length} of {orders.length} orders
            </span>

            <span>
              Page {safeCurrentPage} of {totalPages}
            </span>
          </div>

          <div className="table-container">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Order ID</th>
                  <th>Origin</th>
                  <th>Destination</th>
                  <th>Distance</th>
                  <th>Weight</th>
                  <th>Priority</th>
                  <th>Vehicle</th>
                  <th>Status</th>
                  <th>Assignment</th>
                </tr>
              </thead>

              <tbody>
                {paginatedOrders.map((order) => {
                  const assignment = assignmentMap.get(
                    order.orderId
                  );

                  return (
                    <tr
                      key={order.orderId}
                      className={
                        selectedOrder?.orderId === order.orderId
                          ? "selected-row"
                          : ""
                      }
                      onClick={() =>
                        setSelectedOrder(order)
                      }
                    >
                      <td className="order-id-cell">
                        {order.orderId}
                      </td>

                      <td>{order.originHub}</td>

                      <td>{order.destinationZone}</td>

                      <td>
                        {formatNumber(order.distanceKm)} km
                      </td>

                      <td>
                        {formatNumber(
                          order.packageWeightKg
                        )} kg
                      </td>

                      <td>
                        <span
                          className={`priority-badge priority-${order.priority?.toLowerCase()}`}
                        >
                          {order.priority}
                        </span>
                      </td>

                      <td>
                        {order.requiredVehicleType}
                      </td>

                      <td>{order.deliveryStatus}</td>

                      <td>
                        {assignment ? (
                          <span className="assignment-badge assigned">
                            {assignment.vehicleId}
                          </span>
                        ) : (
                          <span className="assignment-badge unassigned">
                            Unallocated
                          </span>
                        )}
                      </td>
                    </tr>
                  );
                })}

                {paginatedOrders.length === 0 && (
                  <tr>
                    <td
                      colSpan="9"
                      className="empty-table"
                    >
                      No orders match the selected filters.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          <div className="pagination">
            <button
              disabled={safeCurrentPage === 1}
              onClick={() =>
                setCurrentPage(
                  Math.max(1, safeCurrentPage - 1)
                )
              }
            >
              Previous
            </button>

            <div className="page-numbers">
              {Array.from(
                { length: Math.min(totalPages, 7) },
                (_, index) => {
                  let pageNumber = index + 1;

                  if (totalPages > 7) {
                    if (safeCurrentPage <= 4) {
                      pageNumber = index + 1;
                    } else if (
                      safeCurrentPage >=
                      totalPages - 3
                    ) {
                      pageNumber =
                        totalPages - 6 + index;
                    } else {
                      pageNumber =
                        safeCurrentPage - 3 + index;
                    }
                  }

                  return (
                    <button
                      key={pageNumber}
                      className={
                        pageNumber === safeCurrentPage
                          ? "active-page"
                          : ""
                      }
                      onClick={() =>
                        setCurrentPage(pageNumber)
                      }
                    >
                      {pageNumber}
                    </button>
                  );
                }
              )}
            </div>

            <button
              disabled={safeCurrentPage === totalPages}
              onClick={() =>
                setCurrentPage(
                  Math.min(
                    totalPages,
                    safeCurrentPage + 1
                  )
                )
              }
            >
              Next
            </button>
          </div>
        </section>

        <aside className="order-detail-panel">
          {!selectedOrder ? (
            <div className="empty-detail">
              <div className="empty-detail-icon">↗</div>

              <h2>Select an Order</h2>

              <p>
                Click any order in the table to view its
                complete operational and optimization
                details.
              </p>
            </div>
          ) : (
            <>
              <div className="detail-header">
                <div>
                  <div className="detail-label">
                    ORDER
                  </div>

                  <h2>{selectedOrder.orderId}</h2>
                </div>

                <button
                  className="close-detail"
                  onClick={() =>
                    setSelectedOrder(null)
                  }
                >
                  ×
                </button>
              </div>

              <div className="detail-status-row">
                <span
                  className={`priority-badge priority-${selectedOrder.priority?.toLowerCase()}`}
                >
                  {selectedOrder.priority}
                </span>

                <span className="status-badge">
                  {selectedOrder.deliveryStatus}
                </span>
              </div>

              <div className="detail-section">
                <h3>Delivery Details</h3>

                <div className="detail-grid">
                  <div>
                    <span>Origin Hub</span>
                    <strong>
                      {selectedOrder.originHub}
                    </strong>
                  </div>

                  <div>
                    <span>Destination</span>
                    <strong>
                      {selectedOrder.destinationZone}
                    </strong>
                  </div>

                  <div>
                    <span>Distance</span>
                    <strong>
                      {formatNumber(
                        selectedOrder.distanceKm
                      )} km
                    </strong>
                  </div>

                  <div>
                    <span>Package Weight</span>
                    <strong>
                      {formatNumber(
                        selectedOrder.packageWeightKg
                      )} kg
                    </strong>
                  </div>

                  <div>
                    <span>Delivery Window</span>
                    <strong>
                      {selectedOrder.deliveryWindow}
                    </strong>
                  </div>

                  <div>
                    <span>Service Time</span>
                    <strong>
                      {formatNumber(
                        selectedOrder.serviceTimeMin
                      )} min
                    </strong>
                  </div>

                  <div>
                    <span>Required Vehicle</span>
                    <strong>
                      {selectedOrder.requiredVehicleType}
                    </strong>
                  </div>

                  <div>
                    <span>Existing Delay</span>
                    <strong>
                      {formatNumber(
                        selectedOrder.delayMin
                      )} min
                    </strong>
                  </div>
                </div>
              </div>

              <div className="detail-section">
                <h3>Existing Plan</h3>

                <div className="detail-grid">
                  <div>
                    <span>Planned Distance</span>
                    <strong>
                      {formatNumber(
                        selectedOrder.existingPlannedDistanceKm
                      )} km
                    </strong>
                  </div>

                  <div>
                    <span>Estimated Time</span>
                    <strong>
                      {formatNumber(
                        selectedOrder.existingEstimatedTimeMin
                      )} min
                    </strong>
                  </div>
                </div>
              </div>

              <div className="detail-section">
                <h3>Optimization Assignment</h3>

                {selectedAssignment ? (
                  <div className="assignment-detail">
                    <div className="assignment-highlight">
                      <span>Assigned Vehicle</span>
                      <strong>
                        {selectedAssignment.vehicleId}
                      </strong>
                    </div>

                    <div className="detail-grid">
                      <div>
                        <span>Route Sequence</span>
                        <strong>
                          #{selectedAssignment.sequence}
                        </strong>
                      </div>

                      <div>
                        <span>Vehicle Type</span>
                        <strong>
                          {
                            selectedAssignment.route
                              .vehicle?.vehicleType
                          }
                        </strong>
                      </div>

                      <div>
                        <span>Route Distance</span>
                        <strong>
                          {formatNumber(
                            selectedAssignment.route
                              .totalDistanceKm
                          )} km
                        </strong>
                      </div>

                      <div>
                        <span>Route Cost</span>
                        <strong>
                          {formatCurrency(
                            selectedAssignment.route
                              .totalCostInr
                          )}
                        </strong>
                      </div>

                      <div>
                        <span>Planned Day / Trip</span>
                        <strong>
                          Day {selectedAssignment.route.planningDay}
                          {" · "}Trip {selectedAssignment.route.tripNumber ?? 1}
                        </strong>
                      </div>

                      {selectedAssignment.stop && (
                        <div>
                          <span>Planned Service Start</span>
                          <strong>
                            {selectedAssignment.stop.serviceStartTime}
                            {selectedAssignment.stop.onTime
                              ? " (on time)"
                              : " (late)"}
                          </strong>
                        </div>
                      )}
                    </div>
                  </div>
                ) : (
                  <div className="unallocated-detail">
                    <strong>Not allocated</strong>

                    <p>
                      This order could not be assigned
                      to a vehicle by the current
                      optimization algorithm.
                    </p>
                  </div>
                )}
              </div>
            </>
          )}
        </aside>
      </div>
    </div>
  );
}

export default Orders;