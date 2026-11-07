"""
Imports the supplied Excel dataset into PostgreSQL.

Credentials come from environment variables (never hard-code them):
    DB_HOST (default localhost), DB_PORT (5432), DB_NAME (smart_delivery_optimizer),
    DB_USERNAME (postgres), DB_PASSWORD (required)

Alternative without Python: psql -d smart_delivery_optimizer -f database/import_data.sql

Rows without a primary key are skipped and reported. Other missing values are
stored as NULL (not 0), so the backend's DataValidator can report them.
"""

import math
import os
import sys
from pathlib import Path

import pandas as pd
import psycopg2


PROJECT_ROOT = Path(__file__).resolve().parents[2]

DATA_FILE = (
    PROJECT_ROOT
    / "database"
    / "data"
    / "Competitor Beta Dataset for Students.xlsx"
)

DB_CONFIG = {
    "host": os.environ.get("DB_HOST", "localhost"),
    "port": int(os.environ.get("DB_PORT", "5432")),
    "database": os.environ.get("DB_NAME", "smart_delivery_optimizer"),
    "user": os.environ.get("DB_USERNAME", "postgres"),
    "password": os.environ.get("DB_PASSWORD"),
}


def clean(value):
    """Convert pandas NaN/NaT and blank strings to None (SQL NULL); trim text."""
    if value is None:
        return None
    if isinstance(value, float) and math.isnan(value):
        return None
    if isinstance(value, str):
        value = value.strip()
        return value or None
    if hasattr(value, "item"):  # numpy scalar -> Python scalar
        return value.item()
    return value


def main():
    if not DB_CONFIG["password"]:
        sys.exit("DB_PASSWORD environment variable is not set.")

    print(f"Reading: {DATA_FILE}")

    workbook = pd.ExcelFile(DATA_FILE)

    connection = psycopg2.connect(**DB_CONFIG)
    cursor = connection.cursor()

    # ---------------------------------------------------------
    # 1. Vehicle Master
    # ---------------------------------------------------------

    vehicles = pd.read_excel(
        workbook,
        sheet_name="Vehicle Master"
    )

    vehicle_sql = """
        INSERT INTO vehicle_master (
            vehicle_id,
            hub,
            vehicle_type,
            capacity_kg,
            cost_per_km_inr,
            availability,
            max_working_minutes
        )
        VALUES (%s, %s, %s, %s, %s, %s, %s)
        ON CONFLICT (vehicle_id) DO NOTHING
    """

    skipped = 0
    for index, row in vehicles.iterrows():
        values = tuple(clean(row[c]) for c in (
            "Vehicle_ID", "Hub", "Vehicle_Type", "Capacity_Kg",
            "Cost_per_Km_INR", "Availability", "Max_Working_Minutes"))

        # vehicle_master columns are NOT NULL: skip incomplete rows
        if any(v is None for v in values):
            print(f"  Skipping vehicle row {index + 2}: missing required value")
            skipped += 1
            continue

        cursor.execute(vehicle_sql, values)

    print(f"Processed {len(vehicles) - skipped} vehicles ({skipped} skipped).")


    # ---------------------------------------------------------
    # 2. Delivery Orders
    # ---------------------------------------------------------

    orders = pd.read_excel(
        workbook,
        sheet_name="Delivery Orders"
    )

    order_sql = """
        INSERT INTO delivery_orders (
            order_id,
            origin_hub,
            destination_zone,
            distance_km,
            package_weight_kg,
            priority,
            delivery_window,
            required_vehicle_type,
            service_time_min,
            existing_planned_distance_km,
            existing_estimated_time_min,
            delay_min,
            delivery_status
        )
        VALUES (
            %s, %s, %s, %s, %s, %s, %s,
            %s, %s, %s, %s, %s, %s
        )
        ON CONFLICT (order_id) DO NOTHING
    """

    order_columns = (
        "Order_ID", "Origin_Hub", "Destination_Zone", "Distance_Km",
        "Package_Weight_Kg", "Priority", "Delivery_Window",
        "Required_Vehicle_Type", "Service_Time_Min",
        "Existing_Planned_Distance_Km", "Existing_Estimated_Time_Min",
        "Delay_Min", "Delivery_Status")

    skipped = 0
    for index, row in orders.iterrows():
        values = tuple(clean(row[c]) for c in order_columns)

        # order_id, origin_hub and destination_zone are NOT NULL in the schema.
        # Everything else is loaded as-is; the backend validator reports it.
        if values[0] is None or values[1] is None or values[2] is None:
            print(f"  Skipping order row {index + 2}: missing Order_ID, Origin_Hub or Destination_Zone")
            skipped += 1
            continue

        cursor.execute(order_sql, values)

    print(f"Processed {len(orders) - skipped} delivery orders ({skipped} skipped).")


    # ---------------------------------------------------------
    # 3. Zone Distance Matrix
    # ---------------------------------------------------------

    matrix = pd.read_excel(
        workbook,
        sheet_name="Zone Distance Matrix"
    )

    matrix_sql = """
        INSERT INTO zone_distance_matrix (
            from_location,
            to_location,
            distance_km
        )
        VALUES (%s, %s, %s)
        ON CONFLICT (from_location, to_location)
        DO UPDATE SET distance_km = EXCLUDED.distance_km
    """

    for _, row in matrix.iterrows():

        from_location = clean(row["From / To"])

        for to_location in matrix.columns:
            if to_location == "From / To" or from_location is None:
                continue

            distance = row[to_location]

            if pd.isna(distance):
                continue

            cursor.execute(
                matrix_sql,
                (
                    from_location,
                    str(to_location).strip(),
                    float(distance),
                ),
            )

    print("Processed zone distance matrix.")


    # ---------------------------------------------------------
    # Commit
    # ---------------------------------------------------------

    connection.commit()

    cursor.close()
    connection.close()

    print("\nData import completed successfully.")


if __name__ == "__main__":
    main()