package com.logismart.controller;

import com.logismart.algorithm.OptimizationParameters;
import com.logismart.algorithm.RoutePlan;
import com.logismart.kpi.KpiRow;
import com.logismart.model.DeliveryOrder;
import com.logismart.model.OptimizationSummary;
import com.logismart.service.OptimizationRun;
import com.logismart.service.OptimizationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/optimization")
public class OptimizationController {

    private final OptimizationService optimizationService;

    public OptimizationController(OptimizationService optimizationService) {
        this.optimizationService = optimizationService;
    }

    /**
     * Runs the optimizer with the supplied parameters (missing fields use
     * defaults) and returns run metadata. Results are then available from
     * the GET endpoints.
     */
    @PostMapping("/run")
    public ResponseEntity<?> run(@RequestBody(required = false) OptimizationParameters params) {

        OptimizationParameters p = params == null ? OptimizationParameters.defaults() : params;
        List<String> errors = p.validate();

        if (!errors.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid optimization parameters",
                    "details", errors));
        }

        return ResponseEntity.ok(describe(optimizationService.run(p)));
    }

    /** Metadata about the latest run (parameters, data source, validation counts, timing). */
    @GetMapping("/run")
    public Map<String, Object> latestRun() {
        return describe(optimizationService.latest());
    }

    @GetMapping("/parameters/defaults")
    public OptimizationParameters defaultParameters() {
        return OptimizationParameters.defaults();
    }

    @GetMapping("/routes")
    public List<RoutePlan> getRoutes() {
        return optimizationService.getRoutes();
    }

    @GetMapping("/summary")
    public OptimizationSummary getSummary() {
        return optimizationService.getSummary();
    }

    @GetMapping("/kpis")
    public List<KpiRow> getKpis() {
        return optimizationService.getSummary().kpis;
    }

    @GetMapping("/unallocated")
    public List<DeliveryOrder> getUnallocatedOrders() {
        return optimizationService.getUnallocatedOrders();
    }

    @GetMapping("/unallocated/count")
    public int getUnallocatedCount() {
        return optimizationService.getUnallocatedOrders().size();
    }

    @GetMapping("/unallocated/reasons")
    public Map<String, Integer> getUnallocatedReasons() {
        return optimizationService.getUnallocatedReasonCounts();
    }

    @GetMapping("/daily-utilization")
    public List<Map<String, Object>> getDailyFleetUtilization() {
        return optimizationService.getDailyFleetUtilization();
    }

    @GetMapping("/daily-type-utilization")
    public List<Map<String, Object>> getDailyVehicleTypeUtilization() {
        return optimizationService.getDailyVehicleTypeUtilization();
    }

    private static Map<String, Object> describe(OptimizationRun run) {

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("runId", run.runId());
        m.put("generatedAt", run.generatedAt().toString());
        m.put("dataSource", run.dataSource());
        m.put("parameters", run.parameters());
        m.put("totalRuntimeMs", run.totalRuntimeMs());
        m.put("optimizerRuntimeMs", run.summary().optimizerRuntimeMs);
        m.put("trips", run.summary().trips);
        m.put("ordersAllocated", run.summary().ordersAllocated);
        m.put("ordersUnallocated", run.unallocatedOrders().size());
        m.put("planningDays", run.summary().planningDays);
        m.put("validationErrors", run.validation().getErrorCount());
        m.put("validationWarnings", run.validation().getWarningCount());
        m.put("invalidOrders", run.validation().getInvalidOrders());
        return m;
    }
}
