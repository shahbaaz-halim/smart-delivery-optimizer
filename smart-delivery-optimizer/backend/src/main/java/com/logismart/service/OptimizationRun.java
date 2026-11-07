package com.logismart.service;

import com.logismart.algorithm.OptimizationParameters;
import com.logismart.algorithm.RoutePlan;
import com.logismart.model.DeliveryOrder;
import com.logismart.model.OptimizationSummary;
import com.logismart.validation.ValidationReport;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot of one optimization run: inputs, validation outcome
 * and results. All read endpoints serve the latest snapshot, so the
 * optimizer runs once per "Run" instead of once per HTTP request.
 */
public record OptimizationRun(
        String runId,
        Instant generatedAt,
        String dataSource,
        OptimizationParameters parameters,
        ValidationReport validation,
        OptimizationSummary summary,
        List<RoutePlan> routes,
        List<DeliveryOrder> unallocatedOrders,
        int availableVehicles,
        Map<String, Integer> availableVehiclesByType,
        long totalRuntimeMs) {
}
