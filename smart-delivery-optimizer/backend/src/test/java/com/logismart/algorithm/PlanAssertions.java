package com.logismart.algorithm;

import com.logismart.model.DeliveryOrder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Independent checker: re-verifies every hard constraint of a plan without
 * trusting the optimizer's own bookkeeping.
 */
public final class PlanAssertions {

    private static final double EPS = 1e-6;

    private PlanAssertions() {
    }

    public static void assertFeasible(
            List<RoutePlan> routes,
            DistanceMatrix matrix,
            OptimizationParameters params) {

        Set<String> seenOrders = new HashSet<>();
        Map<String, List<RoutePlan>> byVehicleDay = new HashMap<>();
        int dayStart = params.dayStartMinutes();

        for (RoutePlan r : routes) {

            var v = r.getVehicle();
            String trip = v.getVehicleId() + " day " + r.getPlanningDay() + " trip " + r.getTripNumber();

            assertTrue(v.getAvailability().equalsIgnoreCase("Available"), trip + ": vehicle not available");
            assertTrue(!r.getOrders().isEmpty(), trip + ": empty trip");

            // capacity
            double load = r.getOrders().stream().mapToDouble(DeliveryOrder::getPackageWeightKg).sum();
            assertTrue(load <= v.getCapacityKg() + EPS,
                    trip + ": load " + load + " exceeds capacity " + v.getCapacityKg());
            assertEquals(load, r.getTotalWeightKg(), EPS, trip + ": weight bookkeeping");

            // distance recomputed from the sequence
            double km = 0;
            String loc = v.getHub();
            for (DeliveryOrder o : r.getOrders()) {
                km += matrix.distance(loc, o.getDestinationZone());
                loc = o.getDestinationZone();
            }
            km += matrix.distance(loc, v.getHub());
            assertEquals(km, r.getTotalDistanceKm(), 1e-6, trip + ": distance bookkeeping");
            assertEquals(km * v.getCostPerKmInr(), r.getTotalCostInr(), 1e-6, trip + ": cost bookkeeping");

            for (DeliveryOrder o : r.getOrders()) {

                assertTrue(seenOrders.add(o.getOrderId()), "order " + o.getOrderId() + " allocated twice");
                assertTrue(o.getOriginHub().equalsIgnoreCase(v.getHub()),
                        trip + ": order " + o.getOrderId() + " from another hub");

                VehicleClass required = VehicleClass.parse(o.getRequiredVehicleType());
                VehicleClass actual = VehicleClass.parse(v.getVehicleType());

                if (params.isAllowVehicleUpgrade()) {
                    assertTrue(actual.canServe(required), trip + ": vehicle class too small for " + o.getOrderId());
                } else {
                    assertEquals(required, actual, trip + ": type mismatch for " + o.getOrderId());
                }
            }

            // time windows
            if (params.isEnforceTimeWindows()) {
                for (StopSchedule s : r.getStops()) {
                    assertTrue(s.getServiceStartMin() <= s.getWindowEndMin() + EPS,
                            trip + ": " + s.getOrderId() + " starts after its window");
                    assertTrue(s.getServiceStartMin() >= s.getWindowStartMin() - EPS,
                            trip + ": " + s.getOrderId() + " starts before its window");
                }
            }

            assertTrue(r.getStartMin() >= dayStart - EPS, trip + ": starts before day start");
            assertTrue(r.getEndMin() <= dayStart + v.getMaxWorkingMinutes() + EPS,
                    trip + ": ends after shift end");

            byVehicleDay.computeIfAbsent(v.getVehicleId() + "|" + r.getPlanningDay(),
                    k -> new ArrayList<>()).add(r);
        }

        // trips of one vehicle-day must not overlap and must respect reload time
        for (var entry : byVehicleDay.entrySet()) {

            List<RoutePlan> trips = new ArrayList<>(entry.getValue());
            trips.sort(Comparator.comparingDouble(RoutePlan::getStartMin));

            if (!params.isMultiTrip() && trips.size() > 1) {
                fail(entry.getKey() + ": multiple trips although multi-trip is disabled");
            }

            for (int i = 1; i < trips.size(); i++) {
                assertTrue(trips.get(i).getStartMin()
                                >= trips.get(i - 1).getEndMin() + params.getReloadTimeMin() - EPS,
                        entry.getKey() + ": trips overlap or skip reload time");
            }
        }
    }
}
