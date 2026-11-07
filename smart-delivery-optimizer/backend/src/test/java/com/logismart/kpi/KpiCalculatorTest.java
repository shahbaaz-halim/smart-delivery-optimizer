package com.logismart.kpi;

import com.logismart.algorithm.BaselinePlanner;
import com.logismart.kpi.KpiRow.Direction;
import com.logismart.kpi.KpiRow.Status;
import com.logismart.model.DeliveryOrder;
import com.logismart.model.Vehicle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.logismart.TestData.order;
import static com.logismart.TestData.vehicle;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class KpiCalculatorTest {

    private static KpiRow row(Direction d, double baseline, double optimized) {
        return KpiCalculator.row("k", "K", "u", d, baseline, optimized, "", "", "better", "worse");
    }

    @Test
    @DisplayName("TC-KPI-01 lower-is-better: a decrease is IMPROVED with a positive improvement %")
    void lowerIsBetter() {
        KpiRow r = row(Direction.LOWER_IS_BETTER, 200, 150);
        assertEquals(Status.IMPROVED, r.status());
        assertEquals(-25.0, r.changePercent(), 1e-9);
        assertEquals(25.0, r.improvementPercent(), 1e-9);
    }

    @Test
    @DisplayName("TC-KPI-02 lower-is-better: an increase is WORSENED (not hidden as 'trade-off')")
    void lowerIsBetterWorse() {
        KpiRow r = row(Direction.LOWER_IS_BETTER, 100, 130);
        assertEquals(Status.WORSENED, r.status());
        assertEquals("Worse", r.interpretation());
    }

    @Test
    @DisplayName("TC-KPI-03 higher-is-better and tolerance for 'unchanged'")
    void higherIsBetterAndUnchanged() {
        assertEquals(Status.IMPROVED, row(Direction.HIGHER_IS_BETTER, 50, 60).status());
        assertEquals(Status.WORSENED, row(Direction.HIGHER_IS_BETTER, 50, 40).status());
        assertEquals(Status.UNCHANGED, row(Direction.HIGHER_IS_BETTER, 100, 100.01).status());
    }

    @Test
    @DisplayName("TC-KPI-04 zero baseline: no percentage, status judged by direction")
    void zeroBaseline() {
        KpiRow r = row(Direction.LOWER_IS_BETTER, 0, 5);
        assertNull(r.changePercent());
        assertEquals(Status.WORSENED, r.status());
        assertEquals(Status.INFO, row(Direction.NEUTRAL, 3, 9).status());
    }

    @Test
    @DisplayName("TC-BASE-01 baseline sums source fields and uses Delivery_Status for on-time rate")
    void baselineSourceFields() {
        DeliveryOrder a = order("A", "Zone A", 10, "Van");
        DeliveryOrder b = order("B", "Zone A", 10, "Van");
        b.setDeliveryStatus("Delayed");
        b.setDelayMin(35);

        List<Vehicle> fleet = List.of(vehicle("V1", "Hub", "Van", 180, 11, 480));
        BaselinePlanner.BaselineResult r = new BaselinePlanner().plan(List.of(a, b), fleet);

        assertEquals(30.0, r.distanceKm, 1e-9);
        assertEquals(80.0, r.timeMin, 1e-9);
        assertEquals(1, r.onTimeOrders);
        assertEquals(1, r.delayedOrders);
        assertEquals(2, r.allocatedOrders);
        assertEquals(1, r.planningDays);          // (40 + 10) x 2 = 100 min <= 480
        assertEquals(30.0 * 11, r.totalCostInr, 1e-9);
    }
}
