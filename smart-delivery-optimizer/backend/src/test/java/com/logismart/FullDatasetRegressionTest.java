package com.logismart;

import com.logismart.algorithm.BaselinePlanner;
import com.logismart.algorithm.DistanceMatrix;
import com.logismart.algorithm.OptimizationParameters;
import com.logismart.algorithm.OptimizationResult;
import com.logismart.algorithm.PlanAssertions;
import com.logismart.algorithm.RouteOptimizer;
import com.logismart.data.CsvPlanningDataProvider;
import com.logismart.data.PlanningData;
import com.logismart.kpi.KpiCalculator;
import com.logismart.kpi.KpiRow;
import com.logismart.model.OptimizationSummary;
import com.logismart.validation.DataValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the complete pipeline on the supplied 1,800-order dataset (bundled
 * CSV copy) and checks feasibility and the headline improvements. The
 * printed tables are the evidence used in docs/PROJECT_REPORT.md.
 */
class FullDatasetRegressionTest {

    private final PlanningData data = new CsvPlanningDataProvider("classpath:data/").loadAll();
    private final DataValidator.Outcome validation =
            new DataValidator().validate(data.orders(), data.vehicles(), data.distances());
    private final DistanceMatrix matrix = DistanceMatrix.fromEdges(validation.validDistances());

    private OptimizationSummary run(String label, OptimizationParameters p) {

        OptimizationResult result = new RouteOptimizer().optimize(
                validation.validOrders(), validation.validVehicles(), matrix, p);

        PlanAssertions.assertFeasible(result.routes(), matrix, p);

        BaselinePlanner.BaselineResult baseline =
                new BaselinePlanner().plan(validation.validOrders(), validation.validVehicles());
        OptimizationSummary s = new KpiCalculator().summarize(baseline, result);

        System.out.printf(Locale.ROOT, "%n=== %s (%d ms, %d trips, %d 2-opt moves saving %.1f km) ===%n",
                label, result.runtimeMs(), s.trips, s.twoOptImprovements, s.twoOptSavedKm);
        System.out.printf(Locale.ROOT, "%-36s %14s %14s %10s  %s%n",
                "KPI", "Baseline", "Optimized", "Change", "Status");
        for (KpiRow r : s.kpis) {
            System.out.printf(Locale.ROOT, "%-36s %14.2f %14.2f %9s  %s%n",
                    r.name() + " (" + r.unit() + ")", r.baseline(), r.optimized(),
                    r.changePercent() == null ? "-" : String.format(Locale.ROOT, "%+.1f%%", r.changePercent()),
                    r.status());
        }
        System.out.printf(Locale.ROOT, "Unallocated: %d, carried by larger vehicle: %d, wait time: %.0f min%n",
                s.ordersUnallocated, s.ordersCarriedByLargerVehicle, s.totalWaitTimeMin);
        return s;
    }

    @Test
    @DisplayName("TC-DATA-01 supplied dataset passes validation with no errors")
    void datasetIsValid() {
        System.out.println("Validation: " + validation.report().getErrorCount() + " errors, "
                + validation.report().getWarningCount() + " warnings "
                + validation.report().getIssueCountsByRule());
        assertEquals(0, validation.report().getErrorCount());
        assertEquals(1800, validation.validOrders().size());
        assertEquals(52, validation.validVehicles().size());
    }

    @Test
    @DisplayName("TC-E2E-01 v2 optimizer: all 1,800 orders allocated, all constraints hold, KPIs improve")
    void optimizedPlan() {
        OptimizationSummary s = run("v2 default parameters", OptimizationParameters.defaults());

        assertEquals(1800, s.ordersAllocated);
        assertEquals(0, s.ordersUnallocated);
        assertTrue(s.totalDistanceKm < s.baselineDistanceKm, "distance must improve");
        assertTrue(s.totalCostInr < s.baselineTotalCostInr, "cost must improve");
        assertTrue(s.planningDays < s.baselinePlanningDays, "days must improve");
        assertEquals(100.0, s.onTimeRatePercent, 1e-9, "windows are enforced");
        assertTrue(s.optimizerRuntimeMs < 10_000, "should run in seconds");
    }

    @Test
    @DisplayName("TC-E2E-02 ablation: v1.0-like settings (single trip, exact type) are worse than v2")
    void ablation() {
        OptimizationSummary v2 = run("v2 default parameters", OptimizationParameters.defaults());

        OptimizationParameters v1Like = OptimizationParameters.defaults();
        v1Like.setMultiTrip(false);
        v1Like.setAllowVehicleUpgrade(false);
        OptimizationSummary v1 = run("v1.0-like (single trip/day, exact vehicle type)", v1Like);

        OptimizationParameters noWindows = OptimizationParameters.defaults();
        noWindows.setEnforceTimeWindows(false);
        run("v2 with soft time windows", noWindows);

        OptimizationParameters distance = OptimizationParameters.defaults();
        distance.setObjective(OptimizationParameters.Objective.DISTANCE);
        run("v2 with DISTANCE objective", distance);

        assertTrue(v2.planningDays < v1.planningDays, "multi-trip should need fewer days");
    }
}
