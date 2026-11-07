package com.logismart;

import com.logismart.model.DeliveryOrder;
import com.logismart.model.Vehicle;
import com.logismart.model.ZoneDistance;

import java.util.ArrayList;
import java.util.List;

/** Small, readable fixtures for unit tests. */
public final class TestData {

    private TestData() {
    }

    public static DeliveryOrder order(String id, String hub, String zone, double weightKg,
                                      String type, String window) {
        DeliveryOrder o = new DeliveryOrder();
        o.setOrderId(id);
        o.setOriginHub(hub);
        o.setDestinationZone(zone);
        o.setDistanceKm(10);
        o.setPackageWeightKg(weightKg);
        o.setPriority("Medium");
        o.setDeliveryWindow(window);
        o.setRequiredVehicleType(type);
        o.setServiceTimeMin(10);
        o.setExistingPlannedDistanceKm(15);
        o.setExistingEstimatedTimeMin(40);
        o.setDelayMin(0);
        o.setDeliveryStatus("On Time");
        return o;
    }

    public static DeliveryOrder order(String id, String zone, double weightKg, String type) {
        return order(id, "Hub", zone, weightKg, type, "Flexible");
    }

    public static Vehicle vehicle(String id, String hub, String type, double capacity,
                                  double costPerKm, int maxMinutes) {
        Vehicle v = new Vehicle();
        v.setVehicleId(id);
        v.setHub(hub);
        v.setVehicleType(type);
        v.setCapacityKg(capacity);
        v.setCostPerKmInr(costPerKm);
        v.setAvailability("Available");
        v.setMaxWorkingMinutes(maxMinutes);
        return v;
    }

    public static ZoneDistance edge(String from, String to, double km) {
        ZoneDistance d = new ZoneDistance();
        d.setFromLocation(from);
        d.setToLocation(to);
        d.setDistanceKm(km);
        return d;
    }

    /**
     * Symmetric network: Hub, A, B, C.
     * <pre>
     *   Hub-A 10, Hub-B 10, Hub-C 40, A-B 5, A-C 35, B-C 30
     * </pre>
     */
    public static List<ZoneDistance> smallNetwork() {
        List<ZoneDistance> edges = new ArrayList<>();
        String[][] pairs = {
                {"Hub", "Zone A", "10"}, {"Hub", "Zone B", "10"}, {"Hub", "Zone C", "40"},
                {"Zone A", "Zone B", "5"}, {"Zone A", "Zone C", "35"}, {"Zone B", "Zone C", "30"}};
        for (String[] p : pairs) {
            edges.add(edge(p[0], p[1], Double.parseDouble(p[2])));
            edges.add(edge(p[1], p[0], Double.parseDouble(p[2])));
        }
        for (String n : List.of("Hub", "Zone A", "Zone B", "Zone C")) {
            edges.add(edge(n, n, 0));
        }
        return edges;
    }
}
