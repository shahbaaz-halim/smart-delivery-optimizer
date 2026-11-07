package com.logismart.algorithm;

import com.logismart.model.DeliveryOrder;
import com.logismart.model.Vehicle;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Baseline ("current/basic planning") model.
 *
 * <p>Distance, time, delay and on-time figures come straight from the
 * supplied source fields ({@code Existing_Planned_Distance_Km},
 * {@code Existing_Estimated_Time_Min}, {@code Delay_Min},
 * {@code Delivery_Status}), as the Formula Guidance sheet suggests.
 *
 * <p>The dataset has no historical vehicle-to-order allocation, so
 * resource KPIs (days, cost, utilisation) come from a constructed
 * first-fit allocation that mimics basic manual planning:
 * <ul>
 *   <li>each order is dispatched individually (its own planned distance and time)</li>
 *   <li>exact vehicle-type match, same hub, Available vehicles only</li>
 *   <li>orders sorted High &rarr; Low priority, heavier first, then placed on the
 *       first vehicle-day with enough remaining working minutes</li>
 * </ul>
 * This is the same model as v1.0, so baseline numbers stay comparable.
 */
@Component
public class BaselinePlanner {

    private static final int MAX_DAYS = 365;

    public BaselineResult plan(List<DeliveryOrder> orders, List<Vehicle> vehicles) {

        BaselineResult r = new BaselineResult();
        r.orders = orders.size();

        for (DeliveryOrder o : orders) {
            r.distanceKm += o.getExistingPlannedDistanceKm();
            r.timeMin += o.getExistingEstimatedTimeMin();
            r.delayMin += o.getDelayMin();

            String status = o.getDeliveryStatus() == null
                    ? "" : o.getDeliveryStatus().trim().toLowerCase(Locale.ROOT);

            switch (status) {
                case "on time" -> r.onTimeOrders++;
                case "at risk" -> r.atRiskOrders++;
                case "delayed" -> r.delayedOrders++;
                default -> { }
            }
        }

        List<Vehicle> available = vehicles.stream()
                .filter(v -> v.getAvailability() != null
                        && v.getAvailability().trim().equalsIgnoreCase("Available")
                        && v.getMaxWorkingMinutes() > 0)
                .sorted(Comparator.comparing(Vehicle::getVehicleId))
                .toList();

        if (available.isEmpty() || orders.isEmpty()) {
            return r;
        }

        Map<String, Double> workload = new HashMap<>();   // "vehicleId|day" -> minutes
        Map<String, Vehicle> byId = new HashMap<>();
        Set<String> vehiclesUsed = new HashSet<>();
        double loadUtilSum = 0.0;

        available.forEach(v -> byId.put(v.getVehicleId(), v));

        List<DeliveryOrder> sorted = new ArrayList<>(orders);
        sorted.sort(Comparator
                .comparingInt((DeliveryOrder o) -> RouteOptimizer.Context.priorityRank(o.getPriority()))
                .thenComparing(DeliveryOrder::getPackageWeightKg, Comparator.reverseOrder())
                .thenComparing(DeliveryOrder::getOrderId));

        for (DeliveryOrder order : sorted) {

            double orderMinutes = order.getExistingEstimatedTimeMin() + order.getServiceTimeMin();
            boolean placed = false;

            for (int day = 1; day <= MAX_DAYS && !placed; day++) {
                for (Vehicle v : available) {

                    if (!compatible(order, v)) {
                        continue;
                    }

                    String k = v.getVehicleId() + "|" + day;
                    double current = workload.getOrDefault(k, 0.0);

                    if (current + orderMinutes <= v.getMaxWorkingMinutes()) {
                        workload.put(k, current + orderMinutes);
                        vehiclesUsed.add(v.getVehicleId());
                        r.totalCostInr += order.getExistingPlannedDistanceKm() * v.getCostPerKmInr();
                        loadUtilSum += order.getPackageWeightKg() / v.getCapacityKg() * 100.0;
                        r.planningDays = Math.max(r.planningDays, day);
                        r.allocatedOrders++;
                        placed = true;
                        break;
                    }
                }
            }
        }

        r.vehiclesUsed = vehiclesUsed.size();
        r.vehicleDays = workload.size();

        r.averageTimeUtilizationPercent = workload.entrySet().stream()
                .mapToDouble(e -> {
                    Vehicle v = byId.get(e.getKey().substring(0, e.getKey().lastIndexOf('|')));
                    return e.getValue() / v.getMaxWorkingMinutes() * 100.0;
                })
                .average()
                .orElse(0.0);

        r.averageLoadUtilizationPercent =
                r.allocatedOrders == 0 ? 0.0 : loadUtilSum / r.allocatedOrders;

        return r;
    }

    private static boolean compatible(DeliveryOrder o, Vehicle v) {
        return o.getOriginHub() != null
                && o.getOriginHub().trim().equalsIgnoreCase(v.getHub() == null ? "" : v.getHub().trim())
                && o.getRequiredVehicleType() != null
                && o.getRequiredVehicleType().trim().equalsIgnoreCase(
                        v.getVehicleType() == null ? "" : v.getVehicleType().trim())
                && o.getPackageWeightKg() <= v.getCapacityKg();
    }

    /** Baseline figures. Public fields keep this simple value holder compact. */
    public static class BaselineResult {
        public int orders;
        public int allocatedOrders;
        public double distanceKm;
        public double timeMin;
        public double delayMin;
        public int onTimeOrders;
        public int atRiskOrders;
        public int delayedOrders;
        public int planningDays;
        public int vehiclesUsed;
        public int vehicleDays;
        public double totalCostInr;
        public double averageTimeUtilizationPercent;
        public double averageLoadUtilizationPercent;
    }
}
