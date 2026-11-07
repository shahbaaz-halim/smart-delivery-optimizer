import { useEffect, useMemo, useState } from "react";
import { api } from "../services/api";

function Unallocated() {
  const [orders, setOrders] = useState([]);
  const [reasons, setReasons] = useState({});

  const [search, setSearch] = useState("");
  const [reasonFilter, setReasonFilter] = useState("All");

  const [selectedOrder, setSelectedOrder] = useState(null);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    async function loadUnallocated() {
      try {
        setLoading(true);
        setError("");

        const [ordersData, reasonsData] =
          await Promise.all([
            api.getUnallocatedOrders(),
            api.getUnallocatedReasons(),
          ]);

        setOrders(ordersData);
        setReasons(reasonsData);
      } catch (err) {
        setError(err.message);
      } finally {
        setLoading(false);
      }
    }

    loadUnallocated();
  }, []);

  const filteredOrders = useMemo(() => {
    const searchValue = search.trim().toLowerCase();

    return orders.filter((order) => {
      const matchesSearch =
        !searchValue ||
        order.orderId
          ?.toLowerCase()
          .includes(searchValue) ||
        order.originHub
          ?.toLowerCase()
          .includes(searchValue) ||
        order.destinationZone
          ?.toLowerCase()
          .includes(searchValue) ||
        order.requiredVehicleType
          ?.toLowerCase()
          .includes(searchValue);

      const matchesReason =
        reasonFilter === "All" ||
        order.allocationReason === reasonFilter;

      return matchesSearch && matchesReason;
    });
  }, [orders, search, reasonFilter]);

  const totalUnallocated = orders.length;

  const reasonEntries = Object.entries(reasons);

  function formatNumber(value, decimals = 1) {
    if (
      value === null ||
      value === undefined ||
      Number.isNaN(Number(value))
    ) {
      return "-";
    }

    return Number(value).toFixed(decimals);
  }

  if (loading) {
    return (
      <div>
        <h1>Unallocated Orders</h1>
        <p>Loading unallocated orders...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div>
        <h1>Unallocated Orders</h1>

        <div className="error-message">
          <strong>
            Unable to load unallocated orders.
          </strong>

          <p>{error}</p>
        </div>
      </div>
    );
  }

  return (
    <div className="unallocated-page">
      <div className="page-header">
        <div>
          <h1>Unallocated Orders</h1>

          <p>
            Orders that could not be assigned under the
            current optimization constraints.
          </p>
        </div>
      </div>

      <div className="unallocated-summary-grid">
        <div className="unallocated-summary-card">
          <span>Total Unallocated</span>
          <strong>{totalUnallocated}</strong>
        </div>

        {reasonEntries.map(([reason, count]) => (
          <div
            className="unallocated-summary-card"
            key={reason}
          >
            <span>{reason}</span>
            <strong>{count}</strong>
          </div>
        ))}
      </div>

      <div className="unallocated-layout">
        <section className="unallocated-main-panel">
          <div className="unallocated-toolbar">
            <div className="search-box">
              <input
                type="text"
                placeholder="Search order, hub, destination or vehicle..."
                value={search}
                onChange={(event) =>
                  setSearch(event.target.value)
                }
              />
            </div>

            <select
              value={reasonFilter}
              onChange={(event) =>
                setReasonFilter(event.target.value)
              }
            >
              <option value="All">
                All Reasons
              </option>

              {reasonEntries.map(([reason]) => (
                <option key={reason} value={reason}>
                  {reason}
                </option>
              ))}
            </select>
          </div>

          <div className="unallocated-table-header">
            <span>
              Showing {filteredOrders.length} of{" "}
              {totalUnallocated} unallocated orders
            </span>
          </div>

          <div className="table-container">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Order ID</th>
                  <th>Hub</th>
                  <th>Destination</th>
                  <th>Weight</th>
                  <th>Priority</th>
                  <th>Vehicle Type</th>
                  <th>Window</th>
                  <th>Reason</th>
                </tr>
              </thead>

              <tbody>
                {filteredOrders.map((order) => (
                  <tr
                    key={order.orderId}
                    className={
                      selectedOrder?.orderId ===
                      order.orderId
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
                      {formatNumber(
                        order.packageWeightKg
                      )}{" "}
                      kg
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

                    <td>{order.deliveryWindow}</td>

                    <td>
                      <span className="reason-badge">
                        {order.allocationReason}
                      </span>
                    </td>
                  </tr>
                ))}

                {filteredOrders.length === 0 && (
                  <tr>
                    <td
                      colSpan="8"
                      className="empty-table"
                    >
                      No unallocated orders match the
                      selected filters.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </section>

        <aside className="unallocated-detail-panel">
          {!selectedOrder ? (
            <div className="empty-detail">
              <div className="empty-detail-icon">
                !
              </div>

              <h2>Select an Order</h2>

              <p>
                Click an unallocated order to inspect
                its operational constraints.
              </p>
            </div>
          ) : (
            <>
              <div className="detail-header">
                <div>
                  <div className="detail-label">
                    UNALLOCATED ORDER
                  </div>

                  <h2>
                    {selectedOrder.orderId}
                  </h2>
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

                <span className="reason-badge">
                  {selectedOrder.allocationReason}
                </span>
              </div>

              <div className="detail-section">
                <h3>Order Details</h3>

                <div className="detail-grid">
                  <div>
                    <span>Order ID</span>
                    <strong>
                      {selectedOrder.orderId}
                    </strong>
                  </div>

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
                      )}{" "}
                      km
                    </strong>
                  </div>

                  <div>
                    <span>Package Weight</span>
                    <strong>
                      {formatNumber(
                        selectedOrder.packageWeightKg
                      )}{" "}
                      kg
                    </strong>
                  </div>

                  <div>
                    <span>Required Vehicle</span>
                    <strong>
                      {
                        selectedOrder.requiredVehicleType
                      }
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
                      )}{" "}
                      min
                    </strong>
                  </div>
                </div>
              </div>

              <div className="detail-section">
                <h3>Allocation Constraint</h3>

                <div className="unallocated-detail">
                  <strong>
                    {selectedOrder.allocationReason}
                  </strong>

                  <p>
                    This order was not assigned to a
                    vehicle by the current optimization
                    process.
                  </p>
                </div>
              </div>

              <div className="detail-section">
                <h3>Existing Plan</h3>

                <div className="detail-grid">
                  <div>
                    <span>
                      Existing Planned Distance
                    </span>

                    <strong>
                      {formatNumber(
                        selectedOrder.existingPlannedDistanceKm
                      )}{" "}
                      km
                    </strong>
                  </div>

                  <div>
                    <span>
                      Existing Estimated Time
                    </span>

                    <strong>
                      {formatNumber(
                        selectedOrder.existingEstimatedTimeMin
                      )}{" "}
                      min
                    </strong>
                  </div>

                  <div>
                    <span>Existing Delay</span>

                    <strong>
                      {formatNumber(
                        selectedOrder.delayMin
                      )}{" "}
                      min
                    </strong>
                  </div>

                  <div>
                    <span>Delivery Status</span>

                    <strong>
                      {selectedOrder.deliveryStatus}
                    </strong>
                  </div>
                </div>
              </div>
            </>
          )}
        </aside>
      </div>
    </div>
  );
}

export default Unallocated;