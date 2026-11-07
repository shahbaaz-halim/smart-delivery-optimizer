package com.logismart.algorithm;

import com.logismart.TestData;
import com.logismart.model.DeliveryOrder;
import com.logismart.model.Vehicle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.logismart.TestData.order;
import static com.logismart.TestData.vehicle;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteOptimizerTest {

    private final RouteOptimizer optimizer = new RouteOptimizer();
    private final DistanceMatrix matrix = DistanceMatrix.fromEdges(TestData.smallNetwork());

    private OptimizationResult run(List<DeliveryOrder> orders, List<Vehicle> vehicles,
                                   OptimizationParameters p) {
        OptimizationResult r = optimizer.optimize(orders, vehicles, matrix, p);
        PlanAssertions.assertFeasible(r.routes(), matrix, p);
        return r;
    }

    private static int allocated(OptimizationResult r) {
        return r.routes().stream().mapToInt(t -> t.getOrders().size()).sum();
    }

    @Test
    @DisplayName("TC-OPT-01 orders to the same zone are consolidated into one trip")
    void consolidatesSameZone() {
        List<DeliveryOrder> orders = List.of(
                order("O1", "Zone A", 10, "Van"),
                order("O2", "Zone A", 10, "Van"),
                order("O3", "Zone A", 10, "Van"));
        OptimizationResult r = run(orders, List.of(vehicle("V1", "Hub", "Van", 180, 11, 480)),
                OptimizationParameters.defaults());

        assertEquals(1, r.routes().size());
        assertEquals(20.0, r.routes().get(0).getTotalDistanceKm(), 1e-9);   // Hub->A->Hub once
    }

    @Test
    @DisplayName("TC-OPT-02 capacity is never exceeded; a full vehicle reloads for a second trip")
    void capacityAndMultiTrip() {
        List<DeliveryOrder> orders = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            orders.add(order("O" + i, "Zone A", 10, "Bike"));   // 60 kg total, bike holds 18
        }
        OptimizationResult r = run(orders, List.of(vehicle("B1", "Hub", "Bike", 18, 5.5, 480)),
                OptimizationParameters.defaults());

        assertEquals(6, allocated(r));
        assertEquals(6, r.routes().size(), "one 10 kg parcel per 18 kg trip");
        assertTrue(r.routes().stream().allMatch(t -> t.getPlanningDay() == 1),
                "multi-trip fits all six on day 1");
    }

    @Test
    @DisplayName("TC-OPT-03 without multi-trip the same work needs more days")
    void singleTripNeedsMoreDays() {
        List<DeliveryOrder> orders = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            orders.add(order("O" + i, "Zone A", 10, "Bike"));
        }
        OptimizationParameters p = OptimizationParameters.defaults();
        p.setMultiTrip(false);

        OptimizationResult r = run(orders, List.of(vehicle("B1", "Hub", "Bike", 18, 5.5, 480)), p);

        assertEquals(6, allocated(r));
        assertEquals(6, r.routes().stream().mapToInt(RoutePlan::getPlanningDay).max().orElse(0));
    }

    @Test
    @DisplayName("TC-OPT-04 working-time limit is respected")
    void workingTimeLimit() {
        // Hub->C->Hub = 80 km = 120 min at 40 km/h + 10 min service = 130 min per trip.
        // A 300-minute shift fits two trips (130 + 15 reload + 130 = 275) but not three.
        List<DeliveryOrder> orders = List.of(
                order("O1", "Zone C", 100, "Van"),
                order("O2", "Zone C", 100, "Van"),
                order("O3", "Zone C", 100, "Van"));
        OptimizationResult r = run(orders, List.of(vehicle("V1", "Hub", "Van", 100, 11, 300)),
                OptimizationParameters.defaults());

        long day1 = r.routes().stream().filter(t -> t.getPlanningDay() == 1).count();
        assertEquals(2, day1);
        assertEquals(3, allocated(r));
    }

    @Test
    @DisplayName("TC-OPT-05 delivery windows are respected (wait if early, never late)")
    void timeWindows() {
        List<DeliveryOrder> orders = List.of(
                order("LATE", "Hub", "Zone A", 5, "Van", "14:00-17:00"),
                order("EARLY", "Hub", "Zone B", 5, "Van", "08:00-11:00"));
        OptimizationResult r = run(orders, List.of(vehicle("V1", "Hub", "Van", 180, 11, 480)),
                OptimizationParameters.defaults());

        assertEquals(2, allocated(r));
        StopSchedule late = r.routes().stream().flatMap(t -> t.getStops().stream())
                .filter(s -> s.getOrderId().equals("LATE")).findFirst().orElseThrow();
        assertTrue(late.getServiceStartMin() >= 14 * 60, "must wait for the 14:00 window");
        assertTrue(late.isOnTime());
    }

    @Test
    @DisplayName("TC-OPT-06 vehicle upgrade: a Bike order can ride in a Van only when upgrades are on")
    void vehicleUpgrade() {
        List<DeliveryOrder> orders = List.of(order("O1", "Zone A", 5, "Bike"));
        List<Vehicle> onlyVan = List.of(vehicle("V1", "Hub", "Van", 180, 11, 480));

        OptimizationResult withUpgrade = run(orders, onlyVan, OptimizationParameters.defaults());
        assertEquals(1, allocated(withUpgrade));

        OptimizationParameters strict = OptimizationParameters.defaults();
        strict.setAllowVehicleUpgrade(false);
        OptimizationResult noUpgrade = optimizer.optimize(
                List.of(order("O1", "Zone A", 5, "Bike")), onlyVan, matrix, strict);
        assertEquals(0, allocated(noUpgrade));
        assertEquals("No available vehicle of the required type at origin hub",
                noUpgrade.unallocatedOrders().get(0).getAllocationReason());
    }

    @Test
    @DisplayName("TC-OPT-07 a vehicle never serves a larger class (Van order cannot use a Bike)")
    void noDowngrade() {
        OptimizationResult r = optimizer.optimize(
                List.of(order("O1", "Zone A", 5, "Van")),
                List.of(vehicle("B1", "Hub", "Bike", 18, 5.5, 480)),
                matrix, OptimizationParameters.defaults());
        assertEquals(0, allocated(r));
        assertEquals(1, r.unallocatedOrders().size());
    }

    @Test
    @DisplayName("TC-OPT-08 cost objective prefers the cheaper suitable vehicle")
    void prefersCheaperVehicle() {
        OptimizationResult r = run(
                List.of(order("O1", "Zone A", 5, "Bike")),
                List.of(vehicle("T1", "Hub", "Mini Truck", 500, 18.5, 480),
                        vehicle("B1", "Hub", "Bike", 18, 5.5, 480)),
                OptimizationParameters.defaults());
        assertEquals("B1", r.routes().get(0).getVehicle().getVehicleId());
    }

    @Test
    @DisplayName("TC-OPT-09 unavailable vehicles and other hubs are never used")
    void hubAndAvailability() {
        Vehicle maintenance = vehicle("V1", "Hub", "Van", 180, 11, 480);
        maintenance.setAvailability("Maintenance");
        Vehicle otherHub = vehicle("V2", "Zone B", "Van", 180, 11, 480);

        OptimizationResult r = optimizer.optimize(
                List.of(order("O1", "Zone A", 5, "Van")),
                List.of(maintenance, otherHub), matrix, OptimizationParameters.defaults());

        assertEquals(0, allocated(r));
        assertEquals("No available vehicle at origin hub",
                r.unallocatedOrders().get(0).getAllocationReason());
    }

    @Test
    @DisplayName("TC-OPT-10 unreachable destination is reported, not crashed on")
    void unreachableZone() {
        OptimizationResult r = optimizer.optimize(
                List.of(order("O1", "Zone Z", 5, "Van")),
                List.of(vehicle("V1", "Hub", "Van", 180, 11, 480)),
                matrix, OptimizationParameters.defaults());
        assertEquals("Destination zone not reachable in distance network",
                r.unallocatedOrders().get(0).getAllocationReason());
    }

    @Test
    @DisplayName("TC-OPT-11 cheapest insertion visits nearby zones together (Hub-A-B-Hub)")
    void cheapestInsertionOrder() {
        List<DeliveryOrder> orders = List.of(
                order("O1", "Zone A", 5, "Van"),
                order("O2", "Zone B", 5, "Van"));
        OptimizationResult r = run(orders, List.of(vehicle("V1", "Hub", "Van", 180, 11, 480)),
                OptimizationParameters.defaults());
        assertEquals(1, r.routes().size());
        assertEquals(25.0, r.routes().get(0).getTotalDistanceKm(), 1e-9);   // 10 + 5 + 10
    }

    @Test
    @DisplayName("TC-OPT-12 results are deterministic for the same input")
    void deterministic() {
        List<DeliveryOrder> a = new ArrayList<>();
        List<DeliveryOrder> b = new ArrayList<>();
        String[] zones = {"Zone A", "Zone B", "Zone C"};
        for (int i = 0; i < 30; i++) {
            a.add(order("O" + i, zones[i % 3], 3 + i % 7, i % 4 == 0 ? "Bike" : "Van"));
            b.add(order("O" + i, zones[i % 3], 3 + i % 7, i % 4 == 0 ? "Bike" : "Van"));
        }
        List<Vehicle> fleet = List.of(
                vehicle("B1", "Hub", "Bike", 18, 5.5, 480),
                vehicle("V1", "Hub", "Van", 60, 11, 480));

        OptimizationResult r1 = run(a, fleet, OptimizationParameters.defaults());
        OptimizationResult r2 = run(b, fleet, OptimizationParameters.defaults());

        assertEquals(r1.routes().size(), r2.routes().size());
        for (int i = 0; i < r1.routes().size(); i++) {
            assertEquals(r1.routes().get(i).getTotalDistanceKm(), r2.routes().get(i).getTotalDistanceKm());
            assertEquals(r1.routes().get(i).getVehicle().getVehicleId(),
                    r2.routes().get(i).getVehicle().getVehicleId());
        }
    }

    @Test
    @DisplayName("TC-OPT-13 2-opt never makes a plan longer")
    void twoOptNeverWorse() {
        List<DeliveryOrder> withOpt = new ArrayList<>();
        List<DeliveryOrder> withoutOpt = new ArrayList<>();
        String[] zones = {"Zone C", "Zone A", "Zone B", "Zone C", "Zone B", "Zone A"};
        for (int i = 0; i < zones.length; i++) {
            withOpt.add(order("O" + i, zones[i], 5, "Van"));
            withoutOpt.add(order("O" + i, zones[i], 5, "Van"));
        }
        List<Vehicle> fleet = List.of(vehicle("V1", "Hub", "Van", 180, 11, 480));

        OptimizationParameters off = OptimizationParameters.defaults();
        off.setTwoOpt(false);

        double kmWith = run(withOpt, fleet, OptimizationParameters.defaults()).routes().stream()
                .mapToDouble(RoutePlan::getTotalDistanceKm).sum();
        double kmWithout = run(withoutOpt, fleet, off).routes().stream()
                .mapToDouble(RoutePlan::getTotalDistanceKm).sum();

        assertTrue(kmWith <= kmWithout + 1e-9);
    }

    @Test
    @DisplayName("TC-OPT-14 empty input produces an empty plan")
    void emptyInput() {
        OptimizationResult r = optimizer.optimize(List.of(), List.of(), matrix,
                OptimizationParameters.defaults());
        assertTrue(r.routes().isEmpty());
        assertTrue(r.unallocatedOrders().isEmpty());
    }
}
