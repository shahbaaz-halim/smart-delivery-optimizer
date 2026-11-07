# Smart Delivery Route & Resource Optimization (LogiSmart India)

A web-based prototype that reads delivery orders, a vehicle fleet and a zone
distance matrix, validates them, builds an optimized multi-day delivery plan,
and compares it with the current (baseline) plan.

| | |
|---|---|
| **Backend** | Java 21, Spring Boot 4 (REST API, JDBC, Actuator) |
| **Frontend** | React 19 + Vite |
| **Database** | PostgreSQL 16, or the bundled CSV files (no database needed) |
| **Deployment** | Docker images, Docker Compose, Kubernetes manifests |

## Results on the supplied dataset

1,800 orders, 52 vehicles (47 available), 12-location distance matrix. Default parameters.

| KPI | Baseline | Optimized | Change |
|---|---:|---:|---:|
| Orders allocated | 1,800 | 1,800 | – |
| Total travel distance | 77,599 km | 31,276 km | **−59.7%** |
| Total operating cost | ₹789,636 | ₹350,960 | **−55.6%** |
| Total delivery time | 176,139 min | 89,140 min | **−49.4%** |
| Days to deliver all orders | 21 | 6 | **−71.4%** |
| On-time rate (planned) | 67.9% | 100% | **+32.1 pts** |
| Vehicle load utilisation | 26.9% | 85.7% | **+58.8 pts** |
| Vehicle working-time utilisation | 94.6% | 92.0% | −2.7% (worse) |

The optimizer runs in about 0.4 s. For full analysis, limitations and an
ablation study, see [docs/PROJECT_REPORT.md](docs/PROJECT_REPORT.md).

## How it works

1. **Load** orders, vehicles and distances from PostgreSQL or CSV.
2. **Validate** them: 25+ rules covering missing values, ranges, unknown zones and
   hubs, duplicates, malformed windows, and matrix consistency. Records with
   errors are excluded and reported.
3. **Shortest paths:** Floyd–Warshall over the zone graph, computed once.
4. **Optimize:** a multi-day, multi-trip cheapest-insertion heuristic with
   capacity, working-time, delivery-window and vehicle-class constraints,
   followed by 2-opt.
5. **Baseline:** source fields plus a constructed first-fit allocation.
6. **KPIs:** a baseline vs optimized comparison. Status (improved, worsened or
   unchanged) is calculated from the numbers.

## Project structure

```text
backend/            Spring Boot service
  algorithm/        DistanceMatrix, RouteOptimizer, BaselinePlanner, TimeWindow, ...
  validation/       DataValidator and report model
  data/             PostgreSQL and CSV data providers, CSV parser
  kpi/              KpiCalculator (baseline vs optimized)
  service/          OptimizationService (pipeline + cached latest run)
  controller/       REST API
frontend/           React UI (Dashboard, Optimize, Data Quality, Operations, Orders, Vehicles, Trips, Unallocated)
database/           schema.sql, import_data.sql (seed), import/import_data.py, data/ (Excel), test-data/
deploy/k8s/         Kubernetes manifests
docs/               Project report, testing evidence, cloud-native design
```

## Running locally

### Option A: no database (quickest)

```bash
cd backend
APP_DATA_SOURCE=csv DB_HEALTH_ENABLED=false ./mvnw spring-boot:run      # Windows PowerShell: see below
cd ../frontend
npm install
npm run dev                                                             # http://localhost:5173
```

PowerShell: `$env:APP_DATA_SOURCE="csv"; $env:DB_HEALTH_ENABLED="false"; .\mvnw spring-boot:run`

### Option B: PostgreSQL

```bash
createdb smart_delivery_optimizer
psql -d smart_delivery_optimizer -f database/schema.sql
psql -d smart_delivery_optimizer -f database/import_data.sql   # or: DB_PASSWORD=... python database/import/import_data.py

export DB_PASSWORD=<your password>          # never put it in a committed file
cd backend && ./mvnw spring-boot:run
cd ../frontend && npm install && npm run dev
```

### Option C: Docker Compose (everything)

```bash
cp .env.example .env        # set DB_PASSWORD
docker compose up --build   # UI: http://localhost:3000, API: http://localhost:8080
```

### Configuration (environment variables)

| Variable | Default | Purpose |
|---|---|---|
| `APP_DATA_SOURCE` | `database` | `database` or `csv` |
| `DB_URL` | `jdbc:postgresql://localhost:5432/smart_delivery_optimizer` | JDBC URL |
| `DB_USERNAME` / `DB_PASSWORD` | `postgres` / *(empty)* | Database credentials |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:3000` | Browser origins |
| `PORT` | `8080` | API port |

## Tests

```bash
cd backend && ./mvnw test
```

There are 55 automated tests: unit tests, a full-dataset regression with an
independent constraint checker, and HTTP end-to-end tests. None needs a
database. See [docs/TESTING.md](docs/TESTING.md).

## API

| Method | Path | Description |
|---|---|---|
| POST | `/api/optimization/run` | Run with parameters (JSON body; omitted fields use defaults) |
| GET | `/api/optimization/run` | Latest run metadata |
| GET | `/api/optimization/summary` | Baseline and optimized figures plus KPI rows |
| GET | `/api/optimization/kpis` | KPI comparison rows |
| GET | `/api/optimization/routes` | Trips with stop schedules |
| GET | `/api/optimization/unallocated` | Unallocated orders with reasons |
| GET | `/api/optimization/daily-utilization` | Per-day fleet usage |
| GET | `/api/validation/report` | Validation report for the loaded data |
| POST | `/api/validation/orders` | Validate an uploaded orders CSV (`text/csv`) |
| GET | `/api/orders`, `/api/vehicles`, `/api/distances` | Source data |
| GET | `/actuator/health` | Health, liveness and readiness |

## Documentation

- [docs/PROJECT_REPORT.md](docs/PROJECT_REPORT.md): problem, data, algorithm, results, interpretation
- [docs/TESTING.md](docs/TESTING.md): test cases and evidence
- [docs/CLOUD_NATIVE.md](docs/CLOUD_NATIVE.md): services, containers, deployment, scaling

## Version

**v2.0**: validation, optimizer v2, honest KPI comparison, control screens, tests and containerisation.
v1.0 was the baseline release.
