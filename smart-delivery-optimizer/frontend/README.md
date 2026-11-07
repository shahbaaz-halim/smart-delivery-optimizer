# Smart Delivery Route & Resource Optimization System

## About

A web-based prototype for delivery route and resource optimization using delivery orders, vehicle constraints and a zone distance matrix.

## v1.0 – Baseline

This release contains the initial working implementation of the project, including:

- Delivery and vehicle data
- PostgreSQL database
- Java / Spring Boot backend
- Initial route and resource optimization algorithm
- React.js frontend

## Technology Stack

- Java
- Spring Boot
- React.js
- PostgreSQL

## Dataset

- 1,800 delivery orders
- 52 vehicles
- 47 available vehicles
- 12-location distance matrix

## Setup

### PostgreSQL

Create a PostgreSQL database and run:

For reference, Excel datasets are available in: database/data

Update the PostgreSQL password in:

```text
import_data.py
application.properties
```

with your local PostgreSQL password.

```text
database/schema.sql
database/import_data.sql
```

### Backend

```bash
cd backend
mvn spring-boot:run
```

### Frontend

```bash
cd frontend
npm install
npm run dev
```

## Experiment & Improve

This repository provides the **v1.0 baseline** for experimentation.

The main objective is to improve the optimization algorithm. You are encouraged to experiment with different allocation strategies, routing heuristics and optimization approaches.

Use the supplied dataset and compare your results with the baseline implementation.

If you develop an improvement, share the approach and results.

## Version

**v1.0 – Baseline**