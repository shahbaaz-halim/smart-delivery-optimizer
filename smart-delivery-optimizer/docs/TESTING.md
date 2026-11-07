# Testing & Validation Evidence

Run all tests (no database needed):

```bash
cd backend
./mvnw test
```

Latest result: **55 tests, 0 failures, 0 errors** (Java 21, Spring Boot 4.1.1).

| Test class | Tests | Type |
|---|---:|---|
| `DistanceMatrixTest` | 5 | Unit: shortest paths |
| `ParsingTest` | 5 | Unit: time windows, vehicle classes, parameters |
| `DataValidatorTest` | 12 | Unit: invalid and missing input handling |
| `CsvParserTest` | 6 | Unit: ingestion |
| `RouteOptimizerTest` | 14 | Unit: algorithm constraints and behaviour |
| `KpiCalculatorTest` | 5 | Unit: KPI status logic and baseline |
| `FullDatasetRegressionTest` | 3 | Integration: 1,800-order dataset |
| `SmartDeliveryOptimizerApplicationTests` | 5 | End-to-end over HTTP (CSV mode) |

`PlanAssertions` is an independent checker used by every optimizer test. For
each trip it verifies:
- vehicle availability and hub
- vehicle class
- capacity
- recomputed distance and cost
- service starts inside the delivery window
- shift limits
- no overlapping trips, with reload time respected
- no order allocated twice

## Functional tests

| ID | Scenario | Expected | Result |
|---|---|---|---|
| TC-OPT-01 | 3 orders to the same zone | 1 trip, Hub→A→Hub = 20 km | Pass |
| TC-OPT-02 | 6 × 10 kg Bike orders, one 18 kg bike | 6 trips, all on day 1 (multi-trip) | Pass |
| TC-OPT-03 | Same, multi-trip disabled | Needs 6 days | Pass |
| TC-OPT-04 | 300-min shift, 130-min trips | 2 trips on day 1, the third on day 2 | Pass |
| TC-OPT-05 | 14:00–17:00 window | Vehicle waits; service ≥ 14:00; on time | Pass |
| TC-OPT-06 | Bike order, only a Van available | Allocated with upgrades on; unallocated with reason when off | Pass |
| TC-OPT-07 | Van order, only a Bike available | Never downgraded; unallocated | Pass |
| TC-OPT-08 | Bike and Mini Truck both fit | COST objective picks the cheaper Bike | Pass |
| TC-OPT-09 | Vehicle in maintenance, or at another hub | Not used; reason "No available vehicle at origin hub" | Pass |
| TC-OPT-10 | Destination not in the matrix | Reported, not crashed | Pass |
| TC-OPT-11 | Orders to zones A and B | Visited in one trip Hub→A→B→Hub = 25 km | Pass |
| TC-OPT-12 | Same input twice | Identical plans (deterministic) | Pass |
| TC-OPT-13 | 2-opt on vs off | Never longer with 2-opt | Pass |
| TC-OPT-14 | Empty input | Empty plan, no error | Pass |
| TC-DM-01–05 | Distance matrix | Diagonal 0; indirect shorter path found; case-insensitive; unknown → ∞; invalid edges ignored | Pass |
| TC-KPI-01–04 | KPI status | Direction-aware IMPROVED/WORSENED/UNCHANGED; a worse value is never shown as improved; zero baseline handled | Pass |
| TC-BASE-01 | Baseline | Sums source fields; on-time rate from `Delivery_Status` | Pass |

## Invalid-input tests

| ID | Input | Expected | Result |
|---|---|---|---|
| TC-VAL-01 | Clean record | Valid, no issues | Pass |
| TC-VAL-02 | Missing weight (NaN) | ERROR `WEIGHT_INVALID`; order excluded with reason | Pass |
| TC-VAL-03 | Weight 0 or −3 | ERROR | Pass |
| TC-VAL-04 | 650 kg (fleet max 500) | ERROR `WEIGHT_EXCEEDS_FLEET` | Pass |
| TC-VAL-05 | Missing or unknown zone | ERROR `ZONE_MISSING` / `ZONE_UNKNOWN` | Pass |
| TC-VAL-06 | Unknown hub | ERROR `HUB_UNKNOWN` | Pass |
| TC-VAL-07 | Priority "Urgent", window "morning", type "Lorry" | ERROR for each | Pass |
| TC-VAL-08 | Duplicate order ID | Second record rejected | Pass |
| TC-VAL-09 | Negative service time; missing delay | ERROR; WARNING only (order kept) | Pass |
| TC-VAL-10 | 25 kg Bike order | WARNING `VEHICLE_TYPE_UNDERSIZED` (upgrade) | Pass |
| TC-VAL-11 | Vehicles with bad capacity, minutes, availability, type | 4 rejected, 1 kept | Pass |
| TC-VAL-12 | Negative distance; asymmetric pair | ERROR; WARNING | Pass |
| TC-CSV-01–05 | CSV ingestion | Columns matched by name; empty/non-numeric → NaN; quoted commas and CRLF; missing column rejected; wrong column count flagged | Pass |
| TC-TW-01–03, TC-VC-01, TC-PR-01 | Parsing | Windows, vehicle classes and run parameters validated | Pass |

**Manual evidence:** upload
[`database/test-data/invalid_orders_sample.csv`](../database/test-data/invalid_orders_sample.csv)
on the Data Quality screen (or `POST /api/validation/orders`). Observed result:

```text
rowsRead 18 · validOrders 4 · invalidOrders 14 · errors 17 · warnings 11
structural: "Row 19: expected 13 columns but found 4"
TST-002 WEIGHT_INVALID (blank)       TST-003 WEIGHT_INVALID (-4)     TST-004 ZONE_UNKNOWN
TST-005 HUB_UNKNOWN                  TST-006 PRIORITY_INVALID        TST-007/008 WINDOW_INVALID
TST-009 VEHICLE_TYPE_INVALID         TST-010 WEIGHT_EXCEEDS_FLEET    TST-011 VEHICLE_TYPE_UNDERSIZED (warning)
TST-012 SERVICE_TIME_INVALID         TST-001 (2nd) ID_DUPLICATE      row 15 ID_MISSING
TST-015 BASELINE_FIELD_INVALID, STATUS_INVALID (warnings)            TST-016 DISTANCE_MISMATCH (warning)
TST-017 WEIGHT_INVALID ("heavy")     TST-018 short row → several errors
```

## Algorithm and result checks (full dataset)

| ID | Check | Result |
|---|---|---|
| TC-DATA-01 | Supplied dataset validates | 0 errors, 0 warnings; 1,800 orders, 52 vehicles, 144 distances |
| TC-E2E-01 | v2 plan on 1,800 orders | All allocated; 0 constraint violations; distance, cost and days below baseline; on-time 100%; ≈0.4 s |
| TC-E2E-02 | Ablation (v1.0-like, soft windows, distance objective) | All feasible; v2 needs fewer days than v1.0-like (6 vs 20) |

## API tests (real HTTP, application started in CSV mode)

| ID | Request | Expected | Result |
|---|---|---|---|
| TC-API-01 | `GET /actuator/health` | 200, `UP` | Pass |
| TC-API-02 | `GET /api/optimization/summary` | 200 with `kpis` and a calculated `IMPROVED` status | Pass |
| TC-API-03 | `POST /run` valid / speed −5 / objective "FASTEST" | 200 / 400 with field message / 400 | Pass |
| TC-API-04 | `POST /api/validation/orders` with bad rows / bad header | Report (`ZONE_UNKNOWN`, `PRIORITY_INVALID`) / 400 | Pass |
| TC-API-05 | All 11 read endpoints | 200 | Pass |

## Manual UI checklist (capture screenshots for the workbook)

Run with the backend in CSV mode and `npm run dev`, then check:
- [ ] Dashboard: working-time utilisation shows **Worsened** (red); the other directional KPIs show Improved.
- [ ] Optimize: the "v1.0-like" preset → Run → History shows 20 days; the "v2" preset → 6 days.
- [ ] Optimize: speed 0 → Run shows the API's validation message.
- [ ] Trips: selecting a trip shows the stop sequence with arrival and service times and ✓.
- [ ] Data Quality: uploading the sample CSV shows the counts above.
