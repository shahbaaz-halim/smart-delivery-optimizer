package com.logismart.kpi;

import com.logismart.algorithm.BaselinePlanner.BaselineResult;
import com.logismart.algorithm.OptimizationResult;
import com.logismart.algorithm.RoutePlan;
import com.logismart.algorithm.StopSchedule;
import com.logismart.algorithm.VehicleClass;
import com.logismart.kpi.KpiRow.Direction;
import com.logismart.kpi.KpiRow.Status;
import com.logismart.model.DeliveryOrder;
import com.logismart.model.OptimizationSummary;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Calculates optimized-plan KPIs and builds the baseline-vs-optimized
 * comparison. Each row's status comes from the numbers, never hard-coded.
 */
@Component
public class KpiCalculator {

    /** Changes smaller than this (in %) count as "unchanged". */
    static final double TOLERANCE_PERCENT = 0.05;

    public OptimizationSummary summarize(BaselineResult base, OptimizationResult result) {

        OptimizationSummary s = new OptimizationSummary();
        List<RoutePlan> routes = result.routes();

        // ---------------- optimized ----------------
        Set<String> vehicles = new HashSet<>();
        Map<String, double[]> vehicleDaySpan = new HashMap<>();   // [first start, last end, max minutes]
        double loadUtilSum = 0.0;
        int onTime = 0;

        for (RoutePlan r : routes) {

            s.ordersAllocated += r.getOrders().size();
            s.totalDistanceKm += r.getTotalDistanceKm();
            s.totalWeightKg += r.getTotalWeightKg();
            s.totalTimeMin += r.getTotalTimeMin();
            s.totalTravelTimeMin += r.getTravelTimeMin();
            s.totalServiceTimeMin += r.getServiceTimeMin();
            s.totalWaitTimeMin += r.getWaitTimeMin();
            s.totalCostInr += r.getTotalCostInr();
            s.planningDays = Math.max(s.planningDays, r.getPlanningDay());
            loadUtilSum += r.getLoadUtilizationPercent();

            vehicles.add(r.getVehicle().getVehicleId());

            vehicleDaySpan.merge(
                    r.getVehicle().getVehicleId() + "|" + r.getPlanningDay(),
                    new double[]{r.getStartMin(), r.getEndMin(), r.getVehicle().getMaxWorkingMinutes()},
                    (a, b) -> new double[]{Math.min(a[0], b[0]), Math.max(a[1], b[1]), a[2]});

            for (StopSchedule stop : r.getStops()) {
                if (stop.isOnTime()) {
                    onTime++;
                }
                s.totalDelayMin += stop.getLateMin();
            }

            VehicleClass actual = VehicleClass.parse(r.getVehicle().getVehicleType());
            for (DeliveryOrder o : r.getOrders()) {
                VehicleClass required = VehicleClass.parse(o.getRequiredVehicleType());
                if (actual != null && required != null && actual.ordinal() > required.ordinal()) {
                    s.ordersCarriedByLargerVehicle++;
                }
            }
        }

        s.trips = routes.size();
        s.vehiclesUsed = vehicles.size();
        s.vehicleDays = vehicleDaySpan.size();
        s.ordersUnallocated = result.unallocatedOrders().size();

        s.averageVehicleUtilizationPercent = vehicleDaySpan.values().stream()
                .mapToDouble(v -> (v[1] - v[0]) / v[2] * 100.0)
                .average().orElse(0.0);

        s.averageLoadUtilizationPercent = routes.isEmpty() ? 0.0 : loadUtilSum / routes.size();

        s.onTimeRatePercent = pct(onTime, s.ordersAllocated);
        s.averageDelayMin = div(s.totalDelayMin, s.ordersAllocated);

        s.averageOrdersPerDay = div(s.ordersAllocated, s.planningDays);
        s.averageDistancePerDayKm = div(s.totalDistanceKm, s.planningDays);
        s.averageTimePerDayMin = div(s.totalTimeMin, s.planningDays);
        s.averageCostPerDayInr = div(s.totalCostInr, s.planningDays);
        s.averagePlannedTimePerOrderMin = div(s.totalTimeMin, s.ordersAllocated);

        s.twoOptImprovements = result.twoOptImprovements();
        s.twoOptSavedKm = result.twoOptSavedKm();
        s.optimizerRuntimeMs = result.runtimeMs();

        // ---------------- baseline ----------------
        s.baselineOrders = base.orders;
        s.baselineOrdersAllocated = base.allocatedOrders;
        s.baselinePlanningDays = base.planningDays;
        s.baselineVehiclesUsed = base.vehiclesUsed;
        s.baselineTrips = base.vehicleDays;
        s.baselineOrdersPerDay = div(base.orders, base.planningDays);
        s.baselineDistanceKm = base.distanceKm;
        s.baselineTimeMin = base.timeMin;
        s.baselineDelayMin = base.delayMin;
        s.baselineAverageDelayMin = div(base.delayMin, base.orders);
        s.baselineOnTimeOrders = base.onTimeOrders;
        s.baselineAtRiskOrders = base.atRiskOrders;
        s.baselineDelayedOrders = base.delayedOrders;
        s.baselineOnTimeRatePercent = pct(base.onTimeOrders, base.orders);
        s.baselineTotalCostInr = base.totalCostInr;
        s.baselineAverageVehicleUtilizationPercent = base.averageTimeUtilizationPercent;
        s.baselineAverageLoadUtilizationPercent = base.averageLoadUtilizationPercent;

        s.kpis = buildRows(s);

        return s;
    }

    private List<KpiRow> buildRows(OptimizationSummary s) {

        return List.of(
                row("orders_allocated", "Orders Allocated", "orders", Direction.HIGHER_IS_BETTER,
                        s.baselineOrdersAllocated, s.ordersAllocated,
                        "Orders the constructed baseline allocation could place on a compatible vehicle.",
                        "Orders scheduled on a trip by the optimizer.",
                        "more orders planned for delivery", "fewer orders could be planned"),

                row("total_distance", "Total Travel Distance", "km", Direction.LOWER_IS_BETTER,
                        s.baselineDistanceKm, s.totalDistanceKm,
                        "SUM of Existing_Planned_Distance_Km (each order planned individually).",
                        "Sum of trip distances: hub -> stops -> hub, using shortest paths.",
                        "less travel because orders share trips", "more travel than the current plan"),

                row("total_time", "Total Delivery Time", "min", Direction.LOWER_IS_BETTER,
                        s.baselineTimeMin, s.totalTimeMin,
                        "SUM of Existing_Estimated_Time_Min.",
                        "Sum of trip durations: travel at the configured average speed + service time + waiting for windows to open.",
                        "less vehicle time needed", "more vehicle time needed"),

                row("total_cost", "Total Operating Cost", "INR", Direction.LOWER_IS_BETTER,
                        s.baselineTotalCostInr, s.totalCostInr,
                        "Existing_Planned_Distance_Km x cost/km of the vehicle assigned by the baseline allocation.",
                        "Trip distance x cost/km of the assigned vehicle.",
                        "lower transportation cost", "higher transportation cost"),

                row("planning_days", "Days Needed to Deliver All Orders", "days", Direction.LOWER_IS_BETTER,
                        s.baselinePlanningDays, s.planningDays,
                        "Days the baseline first-fit allocation needs within vehicle working-time limits.",
                        "Days the optimized multi-trip plan needs.",
                        "the backlog clears in fewer days", "more days are needed"),

                row("orders_per_day", "Orders Delivered per Day", "orders/day", Direction.HIGHER_IS_BETTER,
                        s.baselineOrdersPerDay, s.averageOrdersPerDay,
                        "Total orders / baseline days.",
                        "Allocated orders / optimized days.",
                        "higher daily throughput", "lower daily throughput"),

                row("time_per_order", "Average Time per Order", "min", Direction.LOWER_IS_BETTER,
                        div(s.baselineTimeMin, s.baselineOrders), s.averagePlannedTimePerOrderMin,
                        "Total baseline time / total orders.",
                        "Total optimized trip time / allocated orders.",
                        "less vehicle time per order", "more vehicle time per order"),

                row("time_utilization", "Vehicle Working-Time Utilisation", "%", Direction.HIGHER_IS_BETTER,
                        s.baselineAverageVehicleUtilizationPercent, s.averageVehicleUtilizationPercent,
                        "Average over used vehicle-days of (estimated time + service time) / max working minutes.",
                        "Average over used vehicle-days of (last trip end - first trip start) / max working minutes.",
                        "vehicles are kept busier", "vehicles have more idle time"),

                row("load_utilization", "Vehicle Load Utilisation", "%", Direction.HIGHER_IS_BETTER,
                        s.baselineAverageLoadUtilizationPercent, s.averageLoadUtilizationPercent,
                        "Average order weight / capacity of the assigned vehicle (one order per dispatch).",
                        "Average trip load / vehicle capacity.",
                        "vehicles leave the hub fuller", "vehicles leave the hub emptier"),

                row("on_time_rate", "On-Time Rate", "%", Direction.HIGHER_IS_BETTER,
                        s.baselineOnTimeRatePercent, s.onTimeRatePercent,
                        "Count of Delivery_Status = On Time / total orders.",
                        "Planned stops whose service starts inside the delivery window / allocated orders.",
                        "more deliveries planned inside their window", "fewer deliveries inside their window"),

                row("average_delay", "Average Delay per Order", "min", Direction.LOWER_IS_BETTER,
                        s.baselineAverageDelayMin, s.averageDelayMin,
                        "AVERAGE of Delay_Min.",
                        "Average planned minutes past the window end (0 when windows are enforced).",
                        "less delay", "more delay"),

                row("vehicles_used", "Vehicles Used", "vehicles", Direction.NEUTRAL,
                        s.baselineVehiclesUsed, s.vehiclesUsed,
                        "Distinct vehicles used by the baseline allocation.",
                        "Distinct vehicles used by the optimized plan.",
                        "", ""));
    }

    static KpiRow row(
            String key, String name, String unit, Direction direction,
            double baseline, double optimized,
            String baselineDef, String optimizedDef,
            String improvedText, String worsenedText) {

        Double change = baseline == 0 ? null : (optimized - baseline) / Math.abs(baseline) * 100.0;

        Double improvement = null;
        Status status;

        if (direction == Direction.NEUTRAL) {
            status = Status.INFO;
        } else if (change != null) {
            improvement = direction == Direction.HIGHER_IS_BETTER ? change : -change;
            status = improvement > TOLERANCE_PERCENT ? Status.IMPROVED
                    : improvement < -TOLERANCE_PERCENT ? Status.WORSENED
                    : Status.UNCHANGED;
        } else {
            // Baseline is 0, so a percentage is undefined: judge by direction only.
            double diff = direction == Direction.HIGHER_IS_BETTER
                    ? optimized - baseline : baseline - optimized;
            status = diff > 1e-9 ? Status.IMPROVED
                    : diff < -1e-9 ? Status.WORSENED
                    : Status.UNCHANGED;
        }

        String interpretation = switch (status) {
            case IMPROVED -> capitalize(improvedText);
            case WORSENED -> capitalize(worsenedText);
            case UNCHANGED -> "No material change";
            case INFO -> "Context only - more vehicles is not better or worse by itself";
        };

        return new KpiRow(key, name, unit, direction, baseline, optimized, change, improvement,
                status, baselineDef, optimizedDef, interpretation);
    }

    private static String capitalize(String s) {
        return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static double div(double a, double b) {
        return b == 0 ? 0.0 : a / b;
    }

    private static double pct(double part, double whole) {
        return whole == 0 ? 0.0 : part / whole * 100.0;
    }
}
