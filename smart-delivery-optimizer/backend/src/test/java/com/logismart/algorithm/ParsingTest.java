package com.logismart.algorithm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParsingTest {

    @Test
    @DisplayName("TC-TW-01 HH:MM-HH:MM windows parse to minutes from midnight")
    void parsesWindow() {
        TimeWindow tw = TimeWindow.parse("08:00-11:00");
        assertEquals(480, tw.startMin());
        assertEquals(660, tw.endMin());
        assertFalse(tw.isFlexible());
    }

    @Test
    @DisplayName("TC-TW-02 Flexible and blank windows are flexible")
    void flexible() {
        assertTrue(TimeWindow.parse("Flexible").isFlexible());
        assertTrue(TimeWindow.parse("flexible ").isFlexible());
        assertTrue(TimeWindow.parse(null).isFlexible());
        assertTrue(TimeWindow.parse("").isFlexible());
    }

    @Test
    @DisplayName("TC-TW-03 malformed or reversed windows are rejected")
    void malformed() {
        assertNull(TimeWindow.parse("8am-11am"));
        assertNull(TimeWindow.parse("11:00-08:00"));
        assertNull(TimeWindow.parse("25:00-26:00"));
        assertNull(TimeWindow.parse("10:00-10:00"));
    }

    @Test
    @DisplayName("TC-VC-01 vehicle classes parse loosely and are ordered Bike < Van < Mini Truck")
    void vehicleClasses() {
        assertEquals(VehicleClass.MINI_TRUCK, VehicleClass.parse("mini_truck"));
        assertEquals(VehicleClass.MINI_TRUCK, VehicleClass.parse(" Mini  Truck "));
        assertNull(VehicleClass.parse("Lorry"));
        assertTrue(VehicleClass.VAN.canServe(VehicleClass.BIKE));
        assertTrue(VehicleClass.VAN.canServe(VehicleClass.VAN));
        assertFalse(VehicleClass.BIKE.canServe(VehicleClass.VAN));
    }

    @Test
    @DisplayName("TC-PR-01 optimization parameters are validated")
    void parameters() {
        assertTrue(OptimizationParameters.defaults().validate().isEmpty());

        OptimizationParameters p = new OptimizationParameters();
        p.setAverageSpeedKmph(0);
        p.setDayStartTime("8 o'clock");
        p.setMaxPlanningDays(0);
        p.setReloadTimeMin(-1);
        assertEquals(4, p.validate().size());
    }
}
