// Relative by default: in development Vite proxies /api to the backend
// (vite.config.js); in containers nginx does the same. Override with
// VITE_API_BASE_URL when the API lives on another origin.
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "/api";

async function request(endpoint, options = {}) {
  const response = await fetch(`${API_BASE_URL}${endpoint}`, options);

  if (!response.ok) {
    let message = `API request failed: ${response.status} ${response.statusText}`;

    try {
      const body = await response.json();
      if (body?.error) {
        const details = Array.isArray(body.details)
          ? body.details.join("; ")
          : body.details;
        message = details ? `${body.error}: ${details}` : body.error;
      }
    } catch {
      // response had no JSON body; keep the generic message
    }

    throw new Error(message);
  }

  return response.json();
}

const get = (endpoint) => request(endpoint);

const post = (endpoint, body, contentType = "application/json") =>
  request(endpoint, {
    method: "POST",
    headers: { "Content-Type": contentType },
    body: contentType === "application/json" ? JSON.stringify(body) : body,
  });

export const api = {
  getOrders: () => get("/orders"),
  getVehicles: () => get("/vehicles"),
  getDistances: () => get("/distances"),

  getRoutes: () => get("/optimization/routes"),
  getOptimizationSummary: () => get("/optimization/summary"),
  getKpis: () => get("/optimization/kpis"),
  getUnallocatedOrders: () => get("/optimization/unallocated"),
  getUnallocatedCount: () => get("/optimization/unallocated/count"),
  getUnallocatedReasons: () => get("/optimization/unallocated/reasons"),
  getDailyFleetUtilization: () => get("/optimization/daily-utilization"),
  getDailyVehicleTypeUtilization: () =>
    get("/optimization/daily-type-utilization"),

  getLatestRun: () => get("/optimization/run"),
  getDefaultParameters: () => get("/optimization/parameters/defaults"),
  runOptimization: (parameters) => post("/optimization/run", parameters),

  getValidationReport: () => get("/validation/report"),
  validateOrdersCsv: (csvText) =>
    post("/validation/orders", csvText, "text/csv"),
};
