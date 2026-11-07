package com.logismart.validation;

import com.logismart.TestData;
import com.logismart.model.DeliveryOrder;
import com.logismart.model.Vehicle;
import com.logismart.model.ZoneDistance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static com.logismart.TestData.edge;
import static com.logismart.TestData.order;
import static com.logismart.TestData.vehicle;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataValidatorTest {

    private final DataValidator validator = new DataValidator();

    private final List<Vehicle> fleet = List.of(
            vehicle("B1", "Hub", "Bike", 18, 5.5, 480),
            vehicle("V1", "Hub", "Van", 180, 11, 480),
            vehicle("T1", "Hub", "Mini Truck", 500, 18.5, 480));

    private DataValidator.Outcome validateOrder(Consumer<DeliveryOrder> mutation) {
        DeliveryOrder o = order("O1", "Zone A", 10, "Van");
        mutation.accept(o);
        return validator.validate(List.of(o), fleet, TestData.smallNetwork());
    }

    private static boolean hasRule(DataValidator.Outcome outcome, String rule,
                                   ValidationIssue.Severity severity) {
        return outcome.report().getIssues().stream()
                .anyMatch(i -> i.rule().equals(rule) && i.severity() == severity);
    }

    @Test
    @DisplayName("TC-VAL-01 a clean record passes with no issues")
    void validRecord() {
        DataValidator.Outcome out = validateOrder(o -> { });
        assertEquals(1, out.validOrders().size());
        assertEquals(0, out.report().getErrorCount());
        assertEquals(0, out.report().getWarningCount());
        assertTrue(out.report().isValid());
    }

    @Test
    @DisplayName("TC-VAL-02 missing weight (NaN) is an error and the order is excluded")
    void missingWeight() {
        DataValidator.Outcome out = validateOrder(o -> o.setPackageWeightKg(Double.NaN));
        assertTrue(hasRule(out, "WEIGHT_INVALID", ValidationIssue.Severity.ERROR));
        assertEquals(0, out.validOrders().size());
        assertTrue(out.orderErrors().values().iterator().next().startsWith("Failed validation:"));
    }

    @Test
    @DisplayName("TC-VAL-03 zero or negative weight is an error")
    void nonPositiveWeight() {
        assertTrue(hasRule(validateOrder(o -> o.setPackageWeightKg(0)), "WEIGHT_INVALID",
                ValidationIssue.Severity.ERROR));
        assertTrue(hasRule(validateOrder(o -> o.setPackageWeightKg(-3)), "WEIGHT_INVALID",
                ValidationIssue.Severity.ERROR));
    }

    @Test
    @DisplayName("TC-VAL-04 weight above the largest vehicle is an error")
    void weightExceedsFleet() {
        DataValidator.Outcome out = validateOrder(o -> {
            o.setPackageWeightKg(650);
            o.setRequiredVehicleType("Mini Truck");
        });
        assertTrue(hasRule(out, "WEIGHT_EXCEEDS_FLEET", ValidationIssue.Severity.ERROR));
    }

    @Test
    @DisplayName("TC-VAL-05 missing and unknown destination zones are errors")
    void zones() {
        assertTrue(hasRule(validateOrder(o -> o.setDestinationZone(null)), "ZONE_MISSING",
                ValidationIssue.Severity.ERROR));
        assertTrue(hasRule(validateOrder(o -> o.setDestinationZone("Zone Q")), "ZONE_UNKNOWN",
                ValidationIssue.Severity.ERROR));
    }

    @Test
    @DisplayName("TC-VAL-06 unknown origin hub is an error")
    void unknownHub() {
        assertTrue(hasRule(validateOrder(o -> o.setOriginHub("Moon Hub")), "HUB_UNKNOWN",
                ValidationIssue.Severity.ERROR));
    }

    @Test
    @DisplayName("TC-VAL-07 invalid priority, window and vehicle type are errors")
    void categoricals() {
        assertTrue(hasRule(validateOrder(o -> o.setPriority("Urgent")), "PRIORITY_INVALID",
                ValidationIssue.Severity.ERROR));
        assertTrue(hasRule(validateOrder(o -> o.setDeliveryWindow("morning")), "WINDOW_INVALID",
                ValidationIssue.Severity.ERROR));
        assertTrue(hasRule(validateOrder(o -> o.setRequiredVehicleType("Lorry")), "VEHICLE_TYPE_INVALID",
                ValidationIssue.Severity.ERROR));
    }

    @Test
    @DisplayName("TC-VAL-08 duplicate order IDs: the second record is rejected")
    void duplicateIds() {
        List<DeliveryOrder> orders = List.of(
                order("DUP", "Zone A", 5, "Van"),
                order("DUP", "Zone B", 5, "Van"));
        DataValidator.Outcome out = validator.validate(orders, fleet, TestData.smallNetwork());
        assertEquals(1, out.validOrders().size());
        assertEquals("Zone A", out.validOrders().get(0).getDestinationZone());
        assertTrue(hasRule(out, "ID_DUPLICATE", ValidationIssue.Severity.ERROR));
    }

    @Test
    @DisplayName("TC-VAL-09 negative service time is an error; bad baseline fields are only warnings")
    void serviceAndBaselineFields() {
        assertTrue(hasRule(validateOrder(o -> o.setServiceTimeMin(-1)), "SERVICE_TIME_INVALID",
                ValidationIssue.Severity.ERROR));

        DataValidator.Outcome out = validateOrder(o -> o.setDelayMin(Double.NaN));
        assertTrue(hasRule(out, "BASELINE_FIELD_INVALID", ValidationIssue.Severity.WARNING));
        assertEquals(1, out.validOrders().size(), "warnings do not exclude the order");
    }

    @Test
    @DisplayName("TC-VAL-10 Bike order heavier than any bike gets an upgrade warning, not an error")
    void undersizedVehicleType() {
        DataValidator.Outcome out = validateOrder(o -> {
            o.setRequiredVehicleType("Bike");
            o.setPackageWeightKg(25);
        });
        assertTrue(hasRule(out, "VEHICLE_TYPE_UNDERSIZED", ValidationIssue.Severity.WARNING));
        assertEquals(1, out.validOrders().size());
    }

    @Test
    @DisplayName("TC-VAL-11 invalid vehicles are excluded (capacity, minutes, availability, type)")
    void invalidVehicles() {
        Vehicle noCapacity = vehicle("X1", "Hub", "Van", Double.NaN, 11, 480);
        Vehicle noMinutes = vehicle("X2", "Hub", "Van", 180, 11, 0);
        Vehicle badStatus = vehicle("X3", "Hub", "Van", 180, 11, 480);
        badStatus.setAvailability("Sometimes");
        Vehicle badType = vehicle("X4", "Hub", "Hovercraft", 180, 11, 480);
        Vehicle ok = vehicle("OK", "Hub", "Van", 180, 11, 480);

        DataValidator.Outcome out = validator.validate(List.of(),
                List.of(noCapacity, noMinutes, badStatus, badType, ok), TestData.smallNetwork());

        assertEquals(1, out.validVehicles().size());
        assertEquals("OK", out.validVehicles().get(0).getVehicleId());
        assertEquals(4, out.report().getErrorCount());
    }

    @Test
    @DisplayName("TC-VAL-12 negative distances are errors; asymmetry and gaps are warnings")
    void distanceMatrix() {
        List<ZoneDistance> edges = new ArrayList<>(List.of(
                edge("A", "B", 10), edge("B", "A", 12),   // asymmetric
                edge("A", "C", -4)));                      // negative
        DataValidator.Outcome out = validator.validate(List.of(), List.of(), edges);

        assertTrue(hasRule(out, "DISTANCE_NEGATIVE", ValidationIssue.Severity.ERROR));
        assertTrue(hasRule(out, "ASYMMETRIC", ValidationIssue.Severity.WARNING));
        assertEquals(2, out.validDistances().size());
    }
}
