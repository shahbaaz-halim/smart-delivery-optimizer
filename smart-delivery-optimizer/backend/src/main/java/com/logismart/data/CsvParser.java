package com.logismart.data;

import com.logismart.model.DeliveryOrder;
import com.logismart.model.Vehicle;
import com.logismart.model.ZoneDistance;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Minimal RFC-4180 style CSV reader for the dataset's three sheets.
 * Columns are matched by header name (case-insensitive, spaces and
 * underscores ignored), so column order does not matter.
 *
 * <p>Empty or non-numeric numeric cells become {@code NaN} so the
 * {@code DataValidator} can report them as missing/invalid instead of
 * silently treating them as 0.
 */
public final class CsvParser {

    public static final List<String> ORDER_COLUMNS = List.of(
            "Order_ID", "Origin_Hub", "Destination_Zone", "Distance_Km",
            "Package_Weight_Kg", "Priority", "Delivery_Window",
            "Required_Vehicle_Type", "Service_Time_Min",
            "Existing_Planned_Distance_Km", "Existing_Estimated_Time_Min",
            "Delay_Min", "Delivery_Status");

    /** Thrown when the file structure (not an individual value) is unusable. */
    public static class CsvFormatException extends RuntimeException {
        public CsvFormatException(String message) {
            super(message);
        }
    }

    /** Parsed orders plus structural row problems (e.g. wrong column count). */
    public record OrderParseResult(List<DeliveryOrder> orders, List<String> rowProblems) {
    }

    private CsvParser() {
    }

    public static OrderParseResult parseOrders(Reader reader) {

        Table t = Table.read(reader, ORDER_COLUMNS);
        List<DeliveryOrder> orders = new ArrayList<>();

        for (Row r : t.rows) {
            DeliveryOrder o = new DeliveryOrder();
            o.setOrderId(r.text("Order_ID"));
            o.setOriginHub(r.text("Origin_Hub"));
            o.setDestinationZone(r.text("Destination_Zone"));
            o.setDistanceKm(r.number("Distance_Km"));
            o.setPackageWeightKg(r.number("Package_Weight_Kg"));
            o.setPriority(r.text("Priority"));
            o.setDeliveryWindow(r.text("Delivery_Window"));
            o.setRequiredVehicleType(r.text("Required_Vehicle_Type"));
            o.setServiceTimeMin(r.number("Service_Time_Min"));
            o.setExistingPlannedDistanceKm(r.number("Existing_Planned_Distance_Km"));
            o.setExistingEstimatedTimeMin(r.number("Existing_Estimated_Time_Min"));
            o.setDelayMin(r.number("Delay_Min"));
            o.setDeliveryStatus(r.text("Delivery_Status"));
            orders.add(o);
        }

        return new OrderParseResult(orders, t.problems);
    }

    public static List<Vehicle> parseVehicles(Reader reader) {

        Table t = Table.read(reader, List.of(
                "Vehicle_ID", "Hub", "Vehicle_Type", "Capacity_Kg",
                "Cost_per_Km_INR", "Availability", "Max_Working_Minutes"));

        List<Vehicle> vehicles = new ArrayList<>();

        for (Row r : t.rows) {
            Vehicle v = new Vehicle();
            v.setVehicleId(r.text("Vehicle_ID"));
            v.setHub(r.text("Hub"));
            v.setVehicleType(r.text("Vehicle_Type"));
            v.setCapacityKg(r.number("Capacity_Kg"));
            v.setCostPerKmInr(r.number("Cost_per_Km_INR"));
            v.setAvailability(r.text("Availability"));
            double minutes = r.number("Max_Working_Minutes");
            v.setMaxWorkingMinutes(Double.isNaN(minutes) ? 0 : (int) Math.round(minutes));
            vehicles.add(v);
        }

        return vehicles;
    }

    public static List<ZoneDistance> parseDistances(Reader reader) {

        Table t = Table.read(reader, List.of("From_Location", "To_Location", "Distance_Km"));
        List<ZoneDistance> distances = new ArrayList<>();

        for (Row r : t.rows) {
            ZoneDistance d = new ZoneDistance();
            d.setFromLocation(r.text("From_Location"));
            d.setToLocation(r.text("To_Location"));
            d.setDistanceKm(r.number("Distance_Km"));
            distances.add(d);
        }

        return distances;
    }

    // ------------------------------------------------------------------

    private static String normHeader(String h) {
        return h == null ? "" : h.replace("﻿", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s_]+", "");
    }

    private record Row(Map<String, Integer> header, List<String> cells) {

        String text(String column) {
            Integer i = header.get(normHeader(column));
            if (i == null || i >= cells.size()) {
                return null;
            }
            String v = cells.get(i).trim();
            return v.isEmpty() ? null : v;
        }

        double number(String column) {
            String v = text(column);
            if (v == null) {
                return Double.NaN;
            }
            try {
                return Double.parseDouble(v.replace(",", ""));
            } catch (NumberFormatException e) {
                return Double.NaN;
            }
        }
    }

    private static final class Table {

        final List<Row> rows = new ArrayList<>();
        final List<String> problems = new ArrayList<>();

        static Table read(Reader reader, List<String> requiredColumns) {

            Table t = new Table();
            List<List<String>> records = records(reader);

            if (records.isEmpty()) {
                throw new CsvFormatException("CSV file is empty");
            }

            Map<String, Integer> header = new HashMap<>();
            List<String> headerCells = records.get(0);

            for (int i = 0; i < headerCells.size(); i++) {
                header.put(normHeader(headerCells.get(i)), i);
            }

            List<String> missing = requiredColumns.stream()
                    .filter(c -> !header.containsKey(normHeader(c)))
                    .toList();

            if (!missing.isEmpty()) {
                throw new CsvFormatException("Missing required column(s): " + String.join(", ", missing));
            }

            for (int i = 1; i < records.size(); i++) {

                List<String> cells = records.get(i);

                if (cells.stream().allMatch(String::isBlank)) {
                    continue;   // skip blank lines
                }

                if (cells.size() != headerCells.size()) {
                    t.problems.add("Row " + (i + 1) + ": expected " + headerCells.size()
                            + " columns but found " + cells.size());
                }

                t.rows.add(new Row(header, cells));
            }

            return t;
        }

        /** Splits CSV text into records, honouring double-quoted fields. */
        private static List<List<String>> records(Reader reader) {

            List<List<String>> out = new ArrayList<>();

            try (BufferedReader in = new BufferedReader(reader)) {

                List<String> current = new ArrayList<>();
                StringBuilder field = new StringBuilder();
                boolean quoted = false;
                int c;

                while ((c = in.read()) != -1) {
                    char ch = (char) c;

                    if (quoted) {
                        if (ch == '"') {
                            in.mark(1);
                            int next = in.read();
                            if (next == '"') {
                                field.append('"');
                            } else {
                                quoted = false;
                                if (next != -1) {
                                    in.reset();
                                }
                            }
                        } else {
                            field.append(ch);
                        }
                    } else if (ch == '"') {
                        quoted = true;
                    } else if (ch == ',') {
                        current.add(field.toString());
                        field.setLength(0);
                    } else if (ch == '\n') {
                        current.add(field.toString());
                        field.setLength(0);
                        out.add(current);
                        current = new ArrayList<>();
                    } else if (ch != '\r') {
                        field.append(ch);
                    }
                }

                if (field.length() > 0 || !current.isEmpty()) {
                    current.add(field.toString());
                    out.add(current);
                }

            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            return out;
        }
    }
}
