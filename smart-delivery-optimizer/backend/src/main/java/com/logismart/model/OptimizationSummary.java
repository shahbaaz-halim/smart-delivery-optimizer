package com.logismart.model;

import com.logismart.kpi.KpiRow;

import java.util.ArrayList;
import java.util.List;

/**
 * Baseline and optimized plan figures, returned by
 * {@code GET /api/optimization/summary}.
 *
 * <p>A plain data-transfer object: public fields keep it readable and are
 * serialized to JSON directly. Field names from v1.0 are unchanged, so
 * existing screens keep working.
 */
public class OptimizationSummary {

    // ---- Optimized plan -------------------------------------------------
    public int planningDays;
    public int vehiclesUsed;
    public int trips;
    public int vehicleDays;
    public int ordersAllocated;
    public int ordersUnallocated;
    public int ordersCarriedByLargerVehicle;

    public double totalDistanceKm;
    public double totalWeightKg;
    public double totalTimeMin;
    public double totalTravelTimeMin;
    public double totalServiceTimeMin;
    public double totalWaitTimeMin;
    public double totalCostInr;

    public double averageOrdersPerDay;
    public double averageDistancePerDayKm;
    public double averageTimePerDayMin;
    public double averageCostPerDayInr;
    public double averagePlannedTimePerOrderMin;

    /** Average share of each used vehicle-day's working minutes that is occupied. */
    public double averageVehicleUtilizationPercent;
    /** Average trip load / vehicle capacity (Formula Guidance definition). */
    public double averageLoadUtilizationPercent;

    public double onTimeRatePercent;
    public double totalDelayMin;
    public double averageDelayMin;

    public int twoOptImprovements;
    public double twoOptSavedKm;
    public long optimizerRuntimeMs;

    // ---- Baseline -------------------------------------------------------
    public int baselineOrders;
    public int baselineOrdersAllocated;
    public int baselinePlanningDays;
    public int baselineVehiclesUsed;
    /** Number of vehicle-days used by the constructed baseline allocation. */
    public int baselineTrips;

    public double baselineOrdersPerDay;
    public double baselineDistanceKm;
    public double baselineTimeMin;
    public double baselineDelayMin;
    public double baselineAverageDelayMin;
    public double baselineOnTimeRatePercent;
    public int baselineOnTimeOrders;
    public int baselineAtRiskOrders;
    public int baselineDelayedOrders;

    public double baselineTotalCostInr;
    public double baselineAverageVehicleUtilizationPercent;
    public double baselineAverageLoadUtilizationPercent;

    // ---- Comparison -----------------------------------------------------
    public List<KpiRow> kpis = new ArrayList<>();
}
