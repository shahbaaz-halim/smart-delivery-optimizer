package com.logismart.validation;

import com.logismart.algorithm.DistanceMatrix;
import com.logismart.algorithm.TimeWindow;
import com.logismart.algorithm.VehicleClass;
import com.logismart.model.DeliveryOrder;
import com.logismart.model.Vehicle;
import com.logismart.model.ZoneDistance;
import com.logismart.validation.ValidationIssue.Severity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Validates orders, vehicles and the distance matrix before optimization.
 *
 * <p>ERROR-level findings exclude the record from optimization (an invalid
 * order is reported as unallocated with the first error as its reason).
 * WARNING-level findings are reported but the record is kept.
 *
 * <p>Missing numeric values arrive as {@code NaN} (the data providers map
 * SQL NULL / empty CSV cells to NaN), so "missing" and "zero" can be told apart.
 *
 * <p>Complexity: O(O + V + D), using HashSets for duplicate detection.
 */
@Component
public class DataValidator {

    private static final Set<String> PRIORITIES = Set.of("high", "medium", "low");
    private static final Set<String> AVAILABILITY = Set.of("available", "maintenance");
    private static final Set<String> STATUSES = Set.of("on time", "at risk", "delayed");

    /** Relative tolerance between Distance_Km and the matrix hub-to-zone distance. */
    private static final double DISTANCE_TOLERANCE = 0.75;

    /** Validation result: the report plus the records that passed. */
    public record Outcome(
            ValidationReport report,
            List<DeliveryOrder> validOrders,
            List<Vehicle> validVehicles,
            List<ZoneDistance> validDistances,
            Map<DeliveryOrder, String> orderErrors) {
    }

    public Outcome validate(
            List<DeliveryOrder> orders,
            List<Vehicle> vehicles,
            List<ZoneDistance> distances) {

        ValidationReport report = new ValidationReport();

        List<ZoneDistance> validDistances = validateDistances(distances, report);
        DistanceMatrix matrix = DistanceMatrix.fromEdges(validDistances);

        List<Vehicle> validVehicles = validateVehicles(vehicles, matrix, report);

        Map<DeliveryOrder, String> orderErrors = new IdentityHashMap<>();
        List<DeliveryOrder> validOrders =
                validateOrders(orders, validVehicles, matrix, report, orderErrors);

        return new Outcome(report, validOrders, validVehicles, validDistances, orderErrors);
    }

    // ------------------------------------------------------------------
    // Distance matrix
    // ------------------------------------------------------------------

    private List<ZoneDistance> validateDistances(
            List<ZoneDistance> distances,
            ValidationReport report) {

        List<ZoneDistance> valid = new ArrayList<>();
        Map<String, Double> lookup = new HashMap<>();
        Set<String> seen = new HashSet<>();
        Set<String> nodes = new HashSet<>();

        for (ZoneDistance d : distances) {

            String id = d.getFromLocation() + " -> " + d.getToLocation();
            boolean ok = true;

            if (isBlank(d.getFromLocation()) || isBlank(d.getToLocation())) {
                report.add(err("DISTANCE", id, "From/To", "LOCATION_MISSING", "",
                        "Distance record has a missing from/to location"));
                ok = false;
            } else if (!seen.add(norm(d.getFromLocation()) + "|" + norm(d.getToLocation()))) {
                report.add(warn("DISTANCE", id, "From/To", "DISTANCE_DUPLICATE", "",
                        "Duplicate distance pair; the shorter value is used"));
            }

            double km = d.getDistanceKm();

            if (Double.isNaN(km)) {
                report.add(err("DISTANCE", id, "Distance_Km", "DISTANCE_MISSING", "",
                        "Distance value is missing"));
                ok = false;
            } else if (km < 0) {
                report.add(err("DISTANCE", id, "Distance_Km", "DISTANCE_NEGATIVE", fmt(km),
                        "Distance cannot be negative"));
                ok = false;
            } else if (ok && norm(d.getFromLocation()).equals(norm(d.getToLocation())) && km != 0) {
                report.add(warn("DISTANCE", id, "Distance_Km", "DIAGONAL_NON_ZERO", fmt(km),
                        "Distance from a location to itself should be 0; treated as 0"));
            }

            if (ok) {
                valid.add(d);
                lookup.put(norm(d.getFromLocation()) + "|" + norm(d.getToLocation()), km);
                nodes.add(norm(d.getFromLocation()));
                nodes.add(norm(d.getToLocation()));
            }
        }

        // Asymmetry and missing pairs (warnings: shortest paths can still cover gaps)
        Set<String> reportedPairs = new HashSet<>();

        for (String a : nodes) {
            for (String b : nodes) {

                if (a.equals(b)) {
                    continue;
                }

                Double ab = lookup.get(a + "|" + b);
                Double ba = lookup.get(b + "|" + a);

                if (ab == null) {
                    report.add(warn("DISTANCE", a + " -> " + b, "Distance_Km", "PAIR_MISSING", "",
                            "No direct distance; shortest path through other locations is used"));
                } else if (ba != null && Math.abs(ab - ba) > 0.01
                        && reportedPairs.add(a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a)) {
                    report.add(warn("DISTANCE", a + " <-> " + b, "Distance_Km", "ASYMMETRIC",
                            fmt(ab) + " / " + fmt(ba),
                            "Distance differs by direction"));
                }
            }
        }

        report.setTotalDistanceRecords(distances.size());
        report.setValidDistanceRecords(valid.size());

        return valid;
    }

    // ------------------------------------------------------------------
    // Vehicles
    // ------------------------------------------------------------------

    private List<Vehicle> validateVehicles(
            List<Vehicle> vehicles,
            DistanceMatrix matrix,
            ValidationReport report) {

        List<Vehicle> valid = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        int row = 1;

        for (Vehicle v : vehicles) {

            row++;
            String id = isBlank(v.getVehicleId()) ? "row " + row : v.getVehicleId().trim();
            int before = errorCount(report);

            if (isBlank(v.getVehicleId())) {
                report.add(err("VEHICLE", id, "Vehicle_ID", "ID_MISSING", "", "Vehicle ID is missing"));
            } else if (!ids.add(norm(v.getVehicleId()))) {
                report.add(err("VEHICLE", id, "Vehicle_ID", "ID_DUPLICATE", id,
                        "Duplicate vehicle ID; only the first record is used"));
            }

            if (isBlank(v.getHub())) {
                report.add(err("VEHICLE", id, "Hub", "HUB_MISSING", "", "Hub is missing"));
            } else if (!matrix.contains(v.getHub())) {
                report.add(err("VEHICLE", id, "Hub", "HUB_UNKNOWN", v.getHub(),
                        "Hub is not in the distance matrix"));
            }

            if (VehicleClass.parse(v.getVehicleType()) == null) {
                report.add(err("VEHICLE", id, "Vehicle_Type", "VEHICLE_TYPE_INVALID",
                        str(v.getVehicleType()), "Vehicle type must be Bike, Van or Mini Truck"));
            }

            checkPositive(report, "VEHICLE", id, "Capacity_Kg", "CAPACITY_INVALID", v.getCapacityKg());

            if (Double.isNaN(v.getCostPerKmInr()) || v.getCostPerKmInr() < 0) {
                report.add(err("VEHICLE", id, "Cost_per_Km_INR", "COST_INVALID",
                        fmt(v.getCostPerKmInr()), "Cost per km is missing or negative"));
            } else if (v.getCostPerKmInr() == 0) {
                report.add(warn("VEHICLE", id, "Cost_per_Km_INR", "COST_ZERO", "0",
                        "Cost per km is zero; cost KPIs will understate this vehicle"));
            }

            if (v.getMaxWorkingMinutes() <= 0) {
                report.add(err("VEHICLE", id, "Max_Working_Minutes", "MAX_MINUTES_INVALID",
                        String.valueOf(v.getMaxWorkingMinutes()),
                        "Max working minutes is missing or not positive"));
            } else if (v.getMaxWorkingMinutes() > 24 * 60) {
                report.add(err("VEHICLE", id, "Max_Working_Minutes", "MAX_MINUTES_INVALID",
                        String.valueOf(v.getMaxWorkingMinutes()),
                        "Max working minutes cannot exceed 1440 (one day)"));
            }

            if (v.getAvailability() == null || !AVAILABILITY.contains(norm(v.getAvailability()))) {
                report.add(err("VEHICLE", id, "Availability", "AVAILABILITY_INVALID",
                        str(v.getAvailability()), "Availability must be Available or Maintenance"));
            }

            if (errorCount(report) == before) {
                valid.add(v);
            }
        }

        report.setTotalVehicles(vehicles.size());
        report.setValidVehicles(valid.size());

        return valid;
    }

    // ------------------------------------------------------------------
    // Orders
    // ------------------------------------------------------------------

    private List<DeliveryOrder> validateOrders(
            List<DeliveryOrder> orders,
            List<Vehicle> vehicles,
            DistanceMatrix matrix,
            ValidationReport report,
            Map<DeliveryOrder, String> orderErrors) {

        // Largest capacity per vehicle class across the valid fleet
        Map<VehicleClass, Double> classCapacity = new EnumMap<>(VehicleClass.class);
        double fleetMax = 0.0;

        for (Vehicle v : vehicles) {
            VehicleClass vc = VehicleClass.parse(v.getVehicleType());
            classCapacity.merge(vc, v.getCapacityKg(), Math::max);
            fleetMax = Math.max(fleetMax, v.getCapacityKg());
        }

        List<DeliveryOrder> valid = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        int row = 1;

        for (DeliveryOrder o : orders) {

            row++;
            String id = isBlank(o.getOrderId()) ? "row " + row : o.getOrderId().trim();
            int firstIssue = report.getIssues().size();
            int before = errorCount(report);

            if (isBlank(o.getOrderId())) {
                report.add(err("ORDER", id, "Order_ID", "ID_MISSING", "", "Order ID is missing"));
            } else if (!ids.add(norm(o.getOrderId()))) {
                report.add(err("ORDER", id, "Order_ID", "ID_DUPLICATE", id,
                        "Duplicate order ID; only the first record is used"));
            }

            boolean hubOk = false;
            if (isBlank(o.getOriginHub())) {
                report.add(err("ORDER", id, "Origin_Hub", "HUB_MISSING", "", "Origin hub is missing"));
            } else if (!matrix.contains(o.getOriginHub())) {
                report.add(err("ORDER", id, "Origin_Hub", "HUB_UNKNOWN", o.getOriginHub(),
                        "Origin hub is not in the distance matrix"));
            } else {
                hubOk = true;
            }

            boolean zoneOk = false;
            if (isBlank(o.getDestinationZone())) {
                report.add(err("ORDER", id, "Destination_Zone", "ZONE_MISSING", "",
                        "Destination zone is missing"));
            } else if (!matrix.contains(o.getDestinationZone())) {
                report.add(err("ORDER", id, "Destination_Zone", "ZONE_UNKNOWN", o.getDestinationZone(),
                        "Destination zone is not in the distance matrix"));
            } else {
                zoneOk = true;
            }

            double weight = o.getPackageWeightKg();
            boolean weightOk = checkPositive(report, "ORDER", id, "Package_Weight_Kg",
                    "WEIGHT_INVALID", weight);

            if (weightOk && fleetMax > 0 && weight > fleetMax) {
                report.add(err("ORDER", id, "Package_Weight_Kg", "WEIGHT_EXCEEDS_FLEET", fmt(weight),
                        "Weight exceeds the largest vehicle capacity (" + fmt(fleetMax) + " kg)"));
            }

            if (o.getPriority() == null || !PRIORITIES.contains(norm(o.getPriority()))) {
                report.add(err("ORDER", id, "Priority", "PRIORITY_INVALID", str(o.getPriority()),
                        "Priority must be High, Medium or Low"));
            }

            if (TimeWindow.parse(o.getDeliveryWindow()) == null) {
                report.add(err("ORDER", id, "Delivery_Window", "WINDOW_INVALID", str(o.getDeliveryWindow()),
                        "Delivery window must be HH:MM-HH:MM (end after start) or Flexible"));
            } else if (isBlank(o.getDeliveryWindow())) {
                report.add(warn("ORDER", id, "Delivery_Window", "WINDOW_MISSING", "",
                        "Delivery window missing; treated as Flexible"));
            }

            VehicleClass required = VehicleClass.parse(o.getRequiredVehicleType());
            if (required == null) {
                report.add(err("ORDER", id, "Required_Vehicle_Type", "VEHICLE_TYPE_INVALID",
                        str(o.getRequiredVehicleType()), "Required vehicle type must be Bike, Van or Mini Truck"));
            } else if (weightOk && classCapacity.containsKey(required)
                    && weight > classCapacity.get(required)) {
                report.add(warn("ORDER", id, "Required_Vehicle_Type", "VEHICLE_TYPE_UNDERSIZED",
                        o.getRequiredVehicleType() + " / " + fmt(weight) + " kg",
                        "Weight exceeds every " + required.getLabel()
                                + "; only a larger vehicle (upgrade) can carry it"));
            }

            if (Double.isNaN(o.getServiceTimeMin()) || o.getServiceTimeMin() < 0) {
                report.add(err("ORDER", id, "Service_Time_Min", "SERVICE_TIME_INVALID",
                        fmt(o.getServiceTimeMin()), "Service time is missing or negative"));
            }

            // Baseline-only fields: warnings, because they do not affect the optimizer
            baselineField(report, id, "Existing_Planned_Distance_Km", o.getExistingPlannedDistanceKm());
            baselineField(report, id, "Existing_Estimated_Time_Min", o.getExistingEstimatedTimeMin());
            baselineField(report, id, "Delay_Min", o.getDelayMin());

            if (o.getDeliveryStatus() == null || !STATUSES.contains(norm(o.getDeliveryStatus()))) {
                report.add(warn("ORDER", id, "Delivery_Status", "STATUS_INVALID", str(o.getDeliveryStatus()),
                        "Status should be On Time, At Risk or Delayed (baseline on-time rate)"));
            }

            if (hubOk && zoneOk && !Double.isNaN(o.getDistanceKm())) {
                double matrixKm = matrix.distance(o.getOriginHub(), o.getDestinationZone());
                if (matrixKm > 0
                        && Math.abs(o.getDistanceKm() - matrixKm) / matrixKm > DISTANCE_TOLERANCE) {
                    report.add(warn("ORDER", id, "Distance_Km", "DISTANCE_MISMATCH",
                            fmt(o.getDistanceKm()) + " vs " + fmt(matrixKm),
                            "Order distance differs from the matrix hub-to-zone distance by more than "
                                    + (int) (DISTANCE_TOLERANCE * 100) + "%"));
                }
            }

            if (errorCount(report) == before) {
                valid.add(o);
            } else {
                report.getIssues().subList(firstIssue, report.getIssues().size()).stream()
                        .filter(i -> i.severity() == Severity.ERROR)
                        .findFirst()
                        .ifPresent(i -> orderErrors.put(o, "Failed validation: " + i.message()));
            }
        }

        report.setTotalOrders(orders.size());
        report.setValidOrders(valid.size());

        return valid;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static boolean checkPositive(
            ValidationReport report, String entity, String id,
            String field, String rule, double value) {

        if (Double.isNaN(value)) {
            report.add(err(entity, id, field, rule, "", field + " is missing or not a number"));
            return false;
        }

        if (value <= 0) {
            report.add(err(entity, id, field, rule, fmt(value), field + " must be greater than 0"));
            return false;
        }

        return true;
    }

    private static void baselineField(ValidationReport report, String id, String field, double value) {
        if (Double.isNaN(value) || value < 0) {
            report.add(warn("ORDER", id, field, "BASELINE_FIELD_INVALID", fmt(value),
                    field + " is missing or negative; baseline KPIs may be understated"));
        }
    }

    private static int errorCount(ValidationReport report) {
        int n = 0;
        for (ValidationIssue i : report.getIssues()) {
            if (i.severity() == Severity.ERROR) {
                n++;
            }
        }
        return n;
    }

    private static ValidationIssue err(String entity, String id, String field,
                                       String rule, String value, String msg) {
        return new ValidationIssue(Severity.ERROR, entity, id, field, rule, value, msg);
    }

    private static ValidationIssue warn(String entity, String id, String field,
                                        String rule, String value, String msg) {
        return new ValidationIssue(Severity.WARNING, entity, id, field, rule, value, msg);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private static String str(String s) {
        return s == null ? "" : s;
    }

    private static String fmt(double d) {
        if (Double.isNaN(d)) {
            return "";
        }
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }
}
