package com.logismart.algorithm;

import com.logismart.model.DeliveryOrder;

import java.util.List;

/**
 * Output of {@link RouteOptimizer#optimize}.
 *
 * @param routes             all trips across the planning horizon
 * @param unallocatedOrders  orders that could not be scheduled; each has
 *                           {@code allocationReason} set
 * @param twoOptImprovements number of 2-opt moves that shortened a trip
 * @param twoOptSavedKm      kilometres saved by 2-opt
 * @param runtimeMs          wall-clock runtime of the optimizer
 */
public record OptimizationResult(
        List<RoutePlan> routes,
        List<DeliveryOrder> unallocatedOrders,
        int twoOptImprovements,
        double twoOptSavedKm,
        long runtimeMs) {
}
