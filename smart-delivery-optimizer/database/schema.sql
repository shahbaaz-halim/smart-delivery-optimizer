CREATE TABLE delivery_orders (
    order_id VARCHAR(20) PRIMARY KEY,
    origin_hub VARCHAR(50) NOT NULL,
    destination_zone VARCHAR(50) NOT NULL,
    distance_km NUMERIC(10,2),
    package_weight_kg NUMERIC(10,2),
    priority VARCHAR(20),
    delivery_window VARCHAR(50),
    required_vehicle_type VARCHAR(30),
    service_time_min NUMERIC(10,2),
    existing_planned_distance_km NUMERIC(10,2),
    existing_estimated_time_min NUMERIC(10,2),
    delay_min NUMERIC(10,2),
    delivery_status VARCHAR(30)
);

CREATE TABLE vehicle_master (
    vehicle_id VARCHAR(20) PRIMARY KEY,
    hub VARCHAR(50) NOT NULL,
    vehicle_type VARCHAR(30) NOT NULL,
    capacity_kg NUMERIC(10,2) NOT NULL,
    cost_per_km_inr NUMERIC(10,2) NOT NULL,
    availability VARCHAR(20) NOT NULL,
    max_working_minutes INTEGER NOT NULL
);

CREATE TABLE zone_distance_matrix (
    from_location VARCHAR(50) NOT NULL,
    to_location VARCHAR(50) NOT NULL,
    distance_km NUMERIC(10,2) NOT NULL,
    PRIMARY KEY (from_location, to_location)
);