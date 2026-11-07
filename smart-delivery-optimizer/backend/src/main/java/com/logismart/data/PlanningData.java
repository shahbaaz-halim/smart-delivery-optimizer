package com.logismart.data;

import com.logismart.model.DeliveryOrder;
import com.logismart.model.Vehicle;
import com.logismart.model.ZoneDistance;

import java.util.List;

/** Raw (unvalidated) planning inputs loaded from a data source. */
public record PlanningData(
        List<DeliveryOrder> orders,
        List<Vehicle> vehicles,
        List<ZoneDistance> distances) {
}
