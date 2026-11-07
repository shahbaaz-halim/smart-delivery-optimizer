package com.logismart.algorithm;

/**
 * Planned arrival details for one stop of a trip.
 */
public class StopSchedule {

    private String orderId;
    private String destinationZone;
    private int sequence;
    private double arrivalMin;
    private double serviceStartMin;
    private double departureMin;
    private double windowStartMin;
    private double windowEndMin;
    private double waitMin;
    private double lateMin;

    public boolean isOnTime() {
        return lateMin <= 1e-9;
    }

    public String getArrivalTime() {
        return TimeWindow.format(arrivalMin);
    }

    public String getServiceStartTime() {
        return TimeWindow.format(serviceStartMin);
    }

    public String getDepartureTime() {
        return TimeWindow.format(departureMin);
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getDestinationZone() {
        return destinationZone;
    }

    public void setDestinationZone(String destinationZone) {
        this.destinationZone = destinationZone;
    }

    public int getSequence() {
        return sequence;
    }

    public void setSequence(int sequence) {
        this.sequence = sequence;
    }

    public double getArrivalMin() {
        return arrivalMin;
    }

    public void setArrivalMin(double arrivalMin) {
        this.arrivalMin = arrivalMin;
    }

    public double getServiceStartMin() {
        return serviceStartMin;
    }

    public void setServiceStartMin(double serviceStartMin) {
        this.serviceStartMin = serviceStartMin;
    }

    public double getDepartureMin() {
        return departureMin;
    }

    public void setDepartureMin(double departureMin) {
        this.departureMin = departureMin;
    }

    public double getWindowStartMin() {
        return windowStartMin;
    }

    public void setWindowStartMin(double windowStartMin) {
        this.windowStartMin = windowStartMin;
    }

    public double getWindowEndMin() {
        return windowEndMin;
    }

    public void setWindowEndMin(double windowEndMin) {
        this.windowEndMin = windowEndMin;
    }

    public double getWaitMin() {
        return waitMin;
    }

    public void setWaitMin(double waitMin) {
        this.waitMin = waitMin;
    }

    public double getLateMin() {
        return lateMin;
    }

    public void setLateMin(double lateMin) {
        this.lateMin = lateMin;
    }
}
