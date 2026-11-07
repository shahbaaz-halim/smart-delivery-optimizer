package com.logismart.data;

import com.logismart.model.DeliveryOrder;
import com.logismart.model.Vehicle;
import com.logismart.model.ZoneDistance;
import com.logismart.repository.DeliveryOrderRepository;
import com.logismart.repository.VehicleRepository;
import com.logismart.repository.ZoneDistanceRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/** Loads planning data from PostgreSQL through the JDBC repositories. */
@Component
@ConditionalOnProperty(name = "app.data-source", havingValue = "database", matchIfMissing = true)
public class JdbcPlanningDataProvider implements PlanningDataProvider {

    private final DeliveryOrderRepository orders;
    private final VehicleRepository vehicles;
    private final ZoneDistanceRepository distances;

    public JdbcPlanningDataProvider(
            DeliveryOrderRepository orders,
            VehicleRepository vehicles,
            ZoneDistanceRepository distances) {

        this.orders = orders;
        this.vehicles = vehicles;
        this.distances = distances;
    }

    @Override
    public List<DeliveryOrder> loadOrders() {
        return orders.findAll();
    }

    @Override
    public List<Vehicle> loadVehicles() {
        return vehicles.findAll();
    }

    @Override
    public List<ZoneDistance> loadDistances() {
        return distances.findAll();
    }

    @Override
    public String sourceName() {
        return "PostgreSQL";
    }
}
