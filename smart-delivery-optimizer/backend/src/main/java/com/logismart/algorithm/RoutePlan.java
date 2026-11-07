package com.logismart.algorithm;

import com.logismart.model.DeliveryOrder;
import com.logismart.model.Vehicle;

import java.util.ArrayList;
import java.util.List;

/**
 * One vehicle trip: leaves the home hub, visits its stops in sequence and
 * returns to the hub. With multi-trip planning a vehicle can run several
 * trips on the same planning day ({@link #tripNumber} 1, 2, 3...).
 *
 * <p>The ordered stop sequence is an {@code ArrayList} because the heuristic
 * inserts at arbitrary positions and 2-opt reverses sub-ranges; trips are
 * short (typically 1-10 stops), so O(n) insertion is negligible.
 */
public class RoutePlan {

    private Vehicle vehicle;
    private int planningDay;
    private int tripNumber = 1;
    private List<DeliveryOrder> orders = new ArrayList<>();
    private List<StopSchedule> stops = new ArrayList<>();

    private double startMin;
    private double endMin;

    private double totalDistanceKm;
    private double totalWeightKg;
    private double totalTimeMin;
    private double totalCostInr;

    private double travelTimeMin;
    private double serviceTimeMin;
    private double waitTimeMin;

    public double getLoadUtilizationPercent() {
        if (vehicle == null || vehicle.getCapacityKg() <= 0) {
            return 0.0;
        }
        return totalWeightKg / vehicle.getCapacityKg() * 100.0;
    }

    public String getStartTime() {
        return TimeWindow.format(startMin);
    }

    public String getEndTime() {
        return TimeWindow.format(endMin);
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public void setVehicle(Vehicle vehicle) {
        this.vehicle = vehicle;
    }

    public int getPlanningDay() {
        return planningDay;
    }

    public void setPlanningDay(int planningDay) {
        this.planningDay = planningDay;
    }

    public int getTripNumber() {
        return tripNumber;
    }

    public void setTripNumber(int tripNumber) {
        this.tripNumber = tripNumber;
    }

    public List<DeliveryOrder> getOrders() {
        return orders;
    }

    public void setOrders(List<DeliveryOrder> orders) {
        this.orders = orders;
    }

    public List<StopSchedule> getStops() {
        return stops;
    }

    public void setStops(List<StopSchedule> stops) {
        this.stops = stops;
    }

    public double getStartMin() {
        return startMin;
    }

    public void setStartMin(double startMin) {
        this.startMin = startMin;
    }

    public double getEndMin() {
        return endMin;
    }

    public void setEndMin(double endMin) {
        this.endMin = endMin;
    }

    public double getTotalDistanceKm() {
        return totalDistanceKm;
    }

    public void setTotalDistanceKm(double totalDistanceKm) {
        this.totalDistanceKm = totalDistanceKm;
    }

    public double getTotalWeightKg() {
        return totalWeightKg;
    }

    public void setTotalWeightKg(double totalWeightKg) {
        this.totalWeightKg = totalWeightKg;
    }

    public double getTotalTimeMin() {
        return totalTimeMin;
    }

    public void setTotalTimeMin(double totalTimeMin) {
        this.totalTimeMin = totalTimeMin;
    }

    public double getTotalCostInr() {
        return totalCostInr;
    }

    public void setTotalCostInr(double totalCostInr) {
        this.totalCostInr = totalCostInr;
    }

    public double getTravelTimeMin() {
        return travelTimeMin;
    }

    public void setTravelTimeMin(double travelTimeMin) {
        this.travelTimeMin = travelTimeMin;
    }

    public double getServiceTimeMin() {
        return serviceTimeMin;
    }

    public void setServiceTimeMin(double serviceTimeMin) {
        this.serviceTimeMin = serviceTimeMin;
    }

    public double getWaitTimeMin() {
        return waitTimeMin;
    }

    public void setWaitTimeMin(double waitTimeMin) {
        this.waitTimeMin = waitTimeMin;
    }
}
