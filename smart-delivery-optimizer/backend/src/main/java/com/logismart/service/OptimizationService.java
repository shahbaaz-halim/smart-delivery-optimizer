package com.logismart.service;

import com.logismart.algorithm.BaselinePlanner;
import com.logismart.algorithm.BaselinePlanner.BaselineResult;
import com.logismart.algorithm.DistanceMatrix;
import com.logismart.algorithm.OptimizationParameters;
import com.logismart.algorithm.OptimizationResult;
import com.logismart.algorithm.RouteOptimizer;
import com.logismart.algorithm.RoutePlan;
import com.logismart.data.PlanningData;
import com.logismart.data.PlanningDataProvider;
import com.logismart.kpi.KpiCalculator;
import com.logismart.model.DeliveryOrder;
import com.logismart.model.OptimizationSummary;
import com.logismart.model.Vehicle;
import com.logismart.validation.DataValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Orchestrates one planning run:
 * load &rarr; validate &rarr; baseline &rarr; optimize &rarr; KPIs.
 *
 * <p>The latest {@link OptimizationRun} is cached in memory. Read endpoints
 * serve the cache; {@link #run(OptimizationParameters)} replaces it.
 */
@Service
public class OptimizationService {

    private static final Logger log = LoggerFactory.getLogger(OptimizationService.class);

    private final PlanningDataProvider provider;
    private final DataValidator validator;
    private final RouteOptimizer optimizer;
    private final BaselinePlanner baselinePlanner;
    private final KpiCalculator kpiCalculator;

    private volatile OptimizationRun latest;

    public OptimizationService(
            PlanningDataProvider provider,
            DataValidator validator,
            RouteOptimizer optimizer,
            BaselinePlanner baselinePlanner,
            KpiCalculator kpiCalculator) {

        this.provider = provider;
        this.validator = validator;
        this.optimizer = optimizer;
        this.baselinePlanner = baselinePlanner;
        this.kpiCalculator = kpiCalculator;
    }

    /** Runs the full pipeline with the given parameters and caches the result. */
    public synchronized OptimizationRun run(OptimizationParameters params) {

        long started = System.nanoTime();

        PlanningData data = provider.loadAll();
        DataValidator.Outcome validation =
                validator.validate(data.orders(), data.vehicles(), data.distances());

        DistanceMatrix matrix = DistanceMatrix.fromEdges(validation.validDistances());

        // Baseline uses the same validated orders so both plans cover the same scope.
        BaselineResult baseline =
                baselinePlanner.plan(validation.validOrders(), validation.validVehicles());

        OptimizationResult result = optimizer.optimize(
                validation.validOrders(), validation.validVehicles(), matrix, params);

        // Invalid orders are reported as unallocated with their validation error.
        List<DeliveryOrder> unallocated = new ArrayList<>(result.unallocatedOrders());
        validation.orderErrors().forEach((order, reason) -> {
            order.setAllocationReason(reason);
            unallocated.add(order);
        });
        unallocated.sort(Comparator.comparing(
                o -> o.getOrderId() == null ? "" : o.getOrderId()));

        OptimizationSummary summary = kpiCalculator.summarize(baseline, result);
        summary.ordersUnallocated = unallocated.size();

        Map<String, Integer> availableByType = new TreeMap<>();
        for (Vehicle v : validation.validVehicles()) {
            if (v.getAvailability().trim().equalsIgnoreCase("Available")) {
                availableByType.merge(v.getVehicleType(), 1, Integer::sum);
            }
        }
        int available = availableByType.values().stream().mapToInt(Integer::intValue).sum();

        long totalMs = (System.nanoTime() - started) / 1_000_000;

        OptimizationRun run = new OptimizationRun(
                UUID.randomUUID().toString(),
                Instant.now(),
                provider.sourceName(),
                params,
                validation.report(),
                summary,
                List.copyOf(result.routes()),
                List.copyOf(unallocated),
                available,
                Map.copyOf(availableByType),
                totalMs);

        log.info("Optimization run {}: {} trips, {} allocated, {} unallocated, {} days, {} ms",
                run.runId(), summary.trips, summary.ordersAllocated,
                unallocated.size(), summary.planningDays, totalMs);

        latest = run;
        return run;
    }

    /** Latest run, computing one with default parameters on first use. */
    public OptimizationRun latest() {

        OptimizationRun current = latest;

        if (current != null) {
            return current;
        }

        synchronized (this) {
            return latest != null ? latest : run(OptimizationParameters.defaults());
        }
    }

    public List<RoutePlan> getRoutes() {
        return latest().routes();
    }

    public OptimizationSummary getSummary() {
        return latest().summary();
    }

    public List<DeliveryOrder> getUnallocatedOrders() {
        return latest().unallocatedOrders();
    }

    public Map<String, Integer> getUnallocatedReasonCounts() {
        Map<String, Integer> counts = new TreeMap<>();
        for (DeliveryOrder o : getUnallocatedOrders()) {
            counts.merge(o.getAllocationReason(), 1, Integer::sum);
        }
        return counts;
    }

    /** Per-day fleet usage: distinct vehicles used vs vehicles available. */
    public List<Map<String, Object>> getDailyFleetUtilization() {

        OptimizationRun run = latest();
        Map<Integer, DayAgg> days = new TreeMap<>();

        for (RoutePlan r : run.routes()) {
            days.computeIfAbsent(r.getPlanningDay(), d -> new DayAgg()).add(r);
        }

        List<Map<String, Object>> out = new ArrayList<>();

        days.forEach((day, agg) -> {
            Map<String, Object> m = agg.toMap();
            m.put("planningDay", day);
            m.put("availableVehicles", run.availableVehicles());
            m.put("utilizationPercent", pct(agg.vehicles.size(), run.availableVehicles()));
            out.add(m);
        });

        return out;
    }

    /** Per-day, per-vehicle-type usage. */
    public List<Map<String, Object>> getDailyVehicleTypeUtilization() {

        OptimizationRun run = latest();
        Map<String, Integer> availableByType = run.availableVehiclesByType();

        Map<String, DayAgg> groups = new LinkedHashMap<>();

        run.routes().stream()
                .sorted(Comparator.comparingInt(RoutePlan::getPlanningDay)
                        .thenComparing(r -> r.getVehicle().getVehicleType()))
                .forEach(r -> groups.computeIfAbsent(
                        r.getPlanningDay() + "|" + r.getVehicle().getVehicleType(),
                        k -> new DayAgg()).add(r));

        List<Map<String, Object>> out = new ArrayList<>();

        groups.forEach((key, agg) -> {
            String[] parts = key.split("\\|", 2);
            int available = availableByType.getOrDefault(parts[1], 0);
            Map<String, Object> m = agg.toMap();
            m.put("planningDay", Integer.parseInt(parts[0]));
            m.put("vehicleType", parts[1]);
            m.put("availableVehicles", available);
            m.put("utilizationPercent", pct(agg.vehicles.size(), available));
            out.add(m);
        });

        return out;
    }

    private static double pct(double part, double whole) {
        return whole == 0 ? 0.0 : part / whole * 100.0;
    }

    private static final class DayAgg {

        final Set<String> vehicles = new HashSet<>();
        int trips;
        int orders;
        double distanceKm;
        double timeMin;
        double costInr;
        double weightKg;

        void add(RoutePlan r) {
            vehicles.add(r.getVehicle().getVehicleId());
            trips++;
            orders += r.getOrders().size();
            distanceKm += r.getTotalDistanceKm();
            timeMin += r.getTotalTimeMin();
            costInr += r.getTotalCostInr();
            weightKg += r.getTotalWeightKg();
        }

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("vehiclesUsed", vehicles.size());
            m.put("trips", trips);
            m.put("ordersAllocated", orders);
            m.put("distanceKm", distanceKm);
            m.put("timeMin", timeMin);
            m.put("costInr", costInr);
            m.put("weightKg", weightKg);
            return m;
        }
    }
}
