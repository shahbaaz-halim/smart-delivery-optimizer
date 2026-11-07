# Project Report: Smart Delivery Route & Resource Optimization

Client (fictional): **LogiSmart India**. Minor project: a justified heuristic
solution, not an enterprise vehicle-routing system.

All figures below come from the supplied dataset, running v2.0 with default
parameters. They are reproducible with `./mvnw test -Dtest=FullDatasetRegressionTest`,
which prints every table in this report.

---

## 1. Problem and requirements

**Business problem.** Orders are planned individually with basic manual
rules. This produces avoidable travel (one trip per parcel), uneven vehicle
use and late deliveries (only 67.9% of orders are *On Time*).

**Users.** Dispatch planners, who run the plan and inspect trips, and operations
managers, who compare KPIs and read the interpretation.

**Functional requirements**

| ID | Requirement | Where |
|---|---|---|
| F1 | Load orders, vehicles and distances from the supplied data | `data/` providers, `database/` |
| F2 | Validate records and report missing or invalid values | `validation/DataValidator`, Data Quality screen |
| F3 | Allocate orders to compatible, available vehicles | `algorithm/RouteOptimizer` |
| F4 | Generate routes (stop sequences) using shortest paths | `DistanceMatrix`, insertion + 2-opt |
| F5 | Respect capacity, working time, delivery windows, hub and vehicle class | `RouteOptimizer.simulate` |
| F6 | Compute baseline and optimized KPIs and their change | `BaselinePlanner`, `KpiCalculator` |
| F7 | Web screens for data, controls, plan and KPI comparison | `frontend/src/pages` |
| F8 | Explain unallocated orders | `Unallocated` screen, reasons |

**Non-functional requirements:** full dataset optimized in under 5 s (actual
≈0.4 s); deterministic results; credentials only in environment variables;
stateless, containerised services with health probes; automated tests that
run without a database.

**Success criteria**

| Criterion | Target | Result |
|---|---|---|
| Orders allocated | 100% of valid orders | 1,800 / 1,800 ✅ |
| Total distance | lower than baseline | −59.7% ✅ |
| Operating cost | lower than baseline | −55.6% ✅ |
| On-time rate | higher than baseline | 67.9% → 100% (planned) ✅ |
| Constraint violations | 0 | 0 (independent checker) ✅ |
| Runtime | < 5 s | ≈0.4 s ✅ |

---

## 2. Dataset and validation

| Sheet | Records | Notes |
|---|---|---|
| Delivery Orders | 1,800 | 4 hubs, 8 zones; Van 1,406 · Bike 344 · Mini Truck 50 |
| Vehicle Master | 52 | 47 Available, 5 Maintenance; Bike 18 kg · Van 180 kg · Mini Truck 500 kg; 480 min/day |
| Zone Distance Matrix | 12 × 12 = 144 cells | symmetric, zero diagonal |

The *Client Data Set* workbook holds the data dictionary and formula guidance;
the records are in the *Competitor Beta* workbook.

**Data types:** identifiers and categorical values are strings; distance,
weight, time, delay and cost are decimals; max working minutes is an integer.

**Validation rules** (`DataValidator`). **ERROR** means the record is excluded
and reported; **WARNING** means it is kept and reported.

| Entity | ERROR rules | WARNING rules |
|---|---|---|
| Orders | missing/duplicate ID; missing/unknown hub or zone; weight missing, ≤ 0 or above the largest vehicle; priority not High/Medium/Low; window not `HH:MM-HH:MM` (end after start) or Flexible; unknown vehicle type; service time missing or negative | weight exceeds every vehicle of the required class (upgrade needed); baseline fields missing or negative; unknown status; `Distance_Km` differs from the matrix by > 75%; blank window (treated as Flexible) |
| Vehicles | missing/duplicate ID; unknown hub; unknown type; capacity missing or ≤ 0; cost missing or negative; max minutes ≤ 0 or > 1440; availability not Available/Maintenance | cost = 0 |
| Distances | missing location; missing or negative distance | duplicate pair; non-zero diagonal; asymmetric pair; missing pair (covered by shortest paths) |

Missing numbers are kept distinct from zero: SQL `NULL` and empty or
non-numeric CSV cells become `NaN`. In v1.0, `ResultSet.getDouble` turned them
into 0 without any report.

**Result on the supplied data:** 0 errors, 0 warnings. The data is clean, so
the validation logic is demonstrated with
[`database/test-data/invalid_orders_sample.csv`](../database/test-data/invalid_orders_sample.csv)
(18 rows: 4 valid, 14 rejected, 17 errors and 11 warnings, every rule
exercised) and with unit tests TC-VAL-01 to TC-VAL-12.

**Assumptions**
1. `Required_Vehicle_Type` is the *minimum* class (the data dictionary says
   "Minimum suitable vehicle class"), so Bike < Van < Mini Truck upgrades are allowed.
2. Vehicles start at their home hub, return there after each trip, and can
   reload and go out again within `Max_Working_Minutes`.
3. The shift starts at 08:00, so it runs 08:00–16:00. "Flexible" means any
   time within the shift.
4. Travel time = shortest-path km ÷ 40 km/h (configurable). The baseline time
   is taken from the source fields as given.
5. Orders have no due date. Spreading them over several days is acceptable if
   each delivery falls inside its window on the day it is dispatched.

---

## 3. Data structures

| Structure | Used for | Why |
|---|---|---|
| `HashMap<String,Integer>` + `double[][]` | Location index and all-pairs distance matrix | O(1) distance lookup inside the hot insertion loop |
| `HashMap<String, List<Vehicle>>` | Available vehicles grouped by hub | Candidate vehicles for an order in O(1) |
| `PriorityQueue<DeliveryOrder>` | Daily dispatch queue | Always takes the most urgent order next: O(log n) per poll |
| `ArrayList<DeliveryOrder>` | Stop sequence of a trip | Insertion at any position; 2-opt reverses sub-lists. Trips are short (≈5 stops) |
| `LinkedHashMap<String, List<Trip>>` | Trips per vehicle per day | Ordered trips; the last one is the open trip |
| `HashSet` / `IdentityHashMap` | Duplicate-ID detection; invalid order → reason | O(1) membership |
| `EnumMap<VehicleClass, Double>` | Largest capacity per vehicle class | Ordered enum keys |
| Records (`OptimizationRun`, `KpiRow`, …) | Immutable results | Safe to share between HTTP requests |

---

## 4. Algorithm design

### 4.1 Shortest paths: Floyd–Warshall (once)

```text
dist[i][j] = matrix value (or ∞), dist[i][i] = 0
for k, for i, for j: dist[i][j] = min(dist[i][j], dist[i][k] + dist[k][j])
```

O(V³) with V = 12, about 1,700 relaxations, run once per optimization. v1.0
ran Dijkstra for every insertion check (thousands of times per run). This
also guarantees the triangle inequality, so adding a stop to a trip never
costs more than a separate trip.

### 4.2 Optimizer v2: multi-day, multi-trip cheapest insertion

```text
remove orders that can never be served (record a reason)
for day = 1 .. maxDays while orders remain:
    queue ← PriorityQueue(remaining) ordered by
            priority, window end, window start, zone, weight desc
    while queue not empty:
        o ← queue.poll()
        best ← none
        for v in vehicles at o.hub with class ≥ o.requiredClass and capacity ≥ o.weight:
            t ← v's open (last) trip today
            if t exists and load(t) + o.weight ≤ capacity(v):
                for each position p in t:                     # cheapest insertion
                    s ← simulate(t with o at p)               # O(stops)
                    if s feasible: consider (Δcost(s), class gap, end time)
            start ← day start if v unused today
                    else end(t) + reload  (only if multi-trip)
            s ← simulate(new trip [o] at start)
            if s feasible: consider (cost(s), class gap, end time)
        apply best option, or defer o to the next day
    apply 2-opt to every trip of the day
```

`simulate` walks the stops: travel (km ÷ speed), wait if the window has not
opened yet, check that service starts before the window ends, add service
time, and return to the hub. A trip is feasible when the load ≤ capacity,
every window is met (when enforced) and the trip ends before the shift ends.

**2-opt:** for every i < j, reverse stops i..j. Keep the change if the trip
gets shorter, stays feasible and does not finish later. Repeat until no move
improves the trip.

**Complexity:** O(N · V · S²) per day, where N is the number of queued orders,
V ≤ 13 is the number of vehicles at a hub and S ≈ 5 is the number of stops per
trip. The full dataset takes about 0.4 s.

**Limitations of the heuristic:** greedy, with no backtracking, so it is not
proven optimal. Only the last trip of each vehicle can still accept orders.
2-opt works within a trip, not across trips.

### 4.3 Baseline

The baseline is built the way the Formula Guidance describes:

- **Distance, time, delay and on-time rate** come straight from the source
  fields. The on-time rate is the count of `On Time` orders ÷ total.
- **Days, cost and utilisation** need a vehicle assignment, which the dataset
  does not have. A first-fit allocation constructs one: each order is
  dispatched individually, the vehicle type must match exactly, and orders go
  High → Low priority, heavier first, into the first vehicle-day with
  remaining minutes. This is the same model as v1.0.

---

## 5. Optimization model

**Decision:** which vehicle, day, trip and stop position each order gets.

**Objective** (selectable): minimise Σ trips (distance × cost per km) (COST,
the default), or minimise Σ distance (DISTANCE).

**Hard constraints**
- Vehicle availability = Available; same hub as the order
- vehicle class ≥ required class (exact match if upgrades are disabled)
- trip load ≤ capacity
- every trip of a vehicle-day lies within [day start, day start + Max_Working_Minutes], with reload time between trips
- service starts within the delivery window (can be relaxed to a soft constraint)

**Tie-breaking:** lower score, then smaller class gap (avoid using an
oversized vehicle), then earlier finish.

---

## 6. Java implementation

| Layer | Classes |
|---|---|
| Data | `PlanningDataProvider` → `JdbcPlanningDataProvider` (PostgreSQL), `CsvPlanningDataProvider` + `CsvParser`; repositories map NULL → NaN |
| Validation | `DataValidator`, `ValidationReport`, `ValidationIssue` |
| Algorithm | `DistanceMatrix`, `RouteOptimizer`, `BaselinePlanner`, `TimeWindow`, `VehicleClass`, `OptimizationParameters`, `RoutePlan`, `StopSchedule` |
| KPI | `KpiCalculator`, `KpiRow` |
| Service | `OptimizationService` (pipeline + cached latest `OptimizationRun`) |
| API | `OptimizationController`, `ValidationController`, `ApiExceptionHandler`, plus the read-only data controllers |

The algorithm classes have no database or web dependencies, so they are
unit-tested directly.

## 7. Web application

| Screen | Purpose |
|---|---|
| Dashboard | Plan at a glance, KPI table with calculated status and definition tooltips, final interpretation |
| Optimize | Parameter form (objective, speed, day start, reload time, horizon, four constraint toggles), presets, run history |
| Data Quality | Validation report for the loaded data; upload a CSV to validate it |
| Operations | Daily fleet and vehicle-type utilisation |
| Orders / Vehicles / Trips | Searchable lists; trip detail with stop sequence, arrival, wait and on-time status |
| Unallocated | Orders that could not be planned, with reasons |

---

## 8–9. Baseline vs optimized results

Default parameters: COST objective, 40 km/h, 08:00 start, 15 min reload,
multi-trip on, upgrades on, hard windows, 2-opt on.

| KPI | Baseline | Optimized | Change | Status |
|---|---:|---:|---:|---|
| Orders allocated | 1,800 | 1,800 | 0.0% | Unchanged |
| Total travel distance (km) | 77,598.98 | 31,276.05 | −59.7% | Improved |
| Total delivery time (min) | 176,138.60 | 89,140.14 | −49.4% | Improved |
| Total operating cost (₹) | 789,636.38 | 350,960.28 | −55.6% | Improved |
| Days to deliver all orders | 21 | 6 | −71.4% | Improved |
| Orders per day | 85.71 | 300.00 | +250.0% | Improved |
| Average time per order (min) | 97.85 | 49.52 | −49.4% | Improved |
| Working-time utilisation (%) | 94.56 | 91.98 | −2.7% | **Worsened** |
| Load utilisation (%) | 26.89 | 85.72 | +218.8% | Improved |
| On-time rate (%) | 67.89 | 100.00 | +47.3% | Improved |
| Average delay (min) | 8.76 | 0.00 | −100% | Improved |
| Vehicles used | 46 | 47 | — | Context |

Other figures: 417 trips; 697 orders carried by a larger class than required;
2-opt made 8 moves and saved 83.3 km; 18,660 min of waiting for windows to open.

### Ablation: what each feature contributes

| Configuration | Distance (km) | Cost (₹) | Days | On-time | Working-time util. |
|---|---:|---:|---:|---:|---:|
| Baseline | 77,599 | 789,636 | 21 | 67.9% | 94.6% |
| v1.0-like: one trip/day, exact type (v2 routing) | 40,673 | 396,436 | 20 | 100% | 61.0% |
| **v2 default** | **31,276** | **350,960** | **6** | **100%** | **92.0%** |
| v2, DISTANCE objective | 31,372 | 352,421 | 6 | 100% | 89.7% |
| v2, soft windows | 31,113 | 342,193 | 7 | 86.4% | 91.4% |

Readings:
- **Consolidation and insertion routing** (row 2 vs baseline) give most of the
  distance and cost saving.
- **Multi-trip days and vehicle upgrades** (row 3 vs row 2) cut the days from
  20 to 6 and raise working-time utilisation from 61% to 92%. A van carries
  about 4 orders per trip, so with one trip a day it sat idle for most of its shift.
- **Hard windows are worth keeping.** Relaxing them saves 0.5% distance but
  drops on-time to 86% and adds a day.
- **COST vs DISTANCE objectives** differ by under 0.5%: the cheapest vehicle is
  usually also on the shortest option.

---

## 10. Testing

There are 55 automated tests, all passing. See [TESTING.md](TESTING.md).
`PlanAssertions` independently re-checks every hard constraint and recomputes
distance and cost for each trip of every plan, including the full 1,800-order
run.

## 11. Cloud-native design

See [CLOUD_NATIVE.md](CLOUD_NATIVE.md). In short: a stateless API container, a
static UI container with an nginx reverse proxy, managed PostgreSQL,
environment-based config, health probes, Docker Compose locally and
Kubernetes with an autoscaler in the cloud.

---

## 12. Final interpretation

**What improved**
- **Travel and cost roughly halved** (−59.7% km, −55.6% ₹). The baseline sends
  each parcel on its own trip; v2 combines orders for the same hub and zone
  into shared trips (load utilisation 27% → 86%).
- **The backlog clears in 6 days instead of 21.** Vehicles reload and run
  several trips a day, and spare larger vehicles take smaller-class orders.
- **Every planned delivery meets its window** (on-time rate 67.9% → 100%,
  average delay 8.8 → 0 min), because windows are checked during insertion.
- **Data problems are now caught and explained** instead of silently becoming
  zeros.

**What did not improve**
- **Working-time utilisation fell slightly** (94.6% → 92.0%). The baseline
  figure is inflated: it adds full per-order trip times, so vehicles look busy
  doing redundant travel. v2 needs far fewer vehicle-minutes; the saving shows
  up as fewer days, not busier days.
- **Vehicles used** stayed about the same (46 → 47). v2 uses the whole
  available fleet to finish sooner.
- **Waiting time:** 18,660 vehicle-minutes are spent waiting for afternoon
  windows to open. This is the next obvious improvement, for example by
  sequencing afternoon-window orders onto later trips.

**Limitations**
1. The baseline's days, cost and utilisation come from a constructed allocation,
   because there are no historical assignments. Its distance and time are
   per-order source values. `Existing_Planned_Distance_Km` (13–81 km) is
   1.12–1.38 × (average 1.25 ×) the one-way hub-to-zone `Distance_Km`, so it appears to exclude the
   return to the hub. The optimized figures always include returns, so if
   that reading is right, the comparison is conservative for v2.
2. Travel uses a constant speed on a 12-node zone matrix: no traffic, no
   street-level routing, and every stop in a zone is treated as the same point.
3. The 100% on-time rate is a *planned* figure. It assumes multi-day dispatch
   is acceptable (no due dates in the data) and that execution follows the plan.
4. The heuristic is greedy and not proven optimal. A metaheuristic (for
   example simulated annealing with relocate/exchange moves) or a MIP solver
   could do better on larger instances.
5. The latest run is cached in memory per backend instance (see CLOUD_NATIVE.md §6).

---

## Demonstration narrative (≈5 minutes)

1. **Data Quality:** the supplied data is valid. Upload
   `invalid_orders_sample.csv` to show each rule rejecting bad rows with reasons.
2. **Optimize:** run the "v1.0-like" preset, then "v2 (recommended)", and
   compare them in Run History (20 days → 6).
3. **Dashboard:** walk through the KPI table: distance, cost, days and on-time
   improved; working-time utilisation worsened slightly, with the explanation.
4. **Trips:** open a trip and show its stop sequence, arrival times, waits and
   on-time ticks.
5. **Architecture:** the Docker Compose file, health probes and environment-based configuration.
