package com.logismart.data;

import com.logismart.model.DeliveryOrder;
import com.logismart.model.Vehicle;
import com.logismart.model.ZoneDistance;

import java.util.List;

/**
 * Source of planning data. Selected with the {@code app.data-source}
 * property: {@code database} (PostgreSQL, default) or {@code csv}
 * (bundled CSV files, no database needed - useful for demos and CI).
 *
 * <p>Every call returns fresh objects, because the optimizer annotates
 * orders (allocation reason) during a run.
 */
public interface PlanningDataProvider {

    List<DeliveryOrder> loadOrders();

    List<Vehicle> loadVehicles();

    List<ZoneDistance> loadDistances();

    /** Short label shown in the UI, e.g. "PostgreSQL" or "CSV (classpath:data/)". */
    String sourceName();

    default PlanningData loadAll() {
        return new PlanningData(loadOrders(), loadVehicles(), loadDistances());
    }
}
