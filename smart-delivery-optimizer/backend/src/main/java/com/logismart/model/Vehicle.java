package com.logismart.model;

public class Vehicle {

    private String vehicleId;
    private String hub;
    private String vehicleType;
    private double capacityKg;
    private double costPerKmInr;
    private String availability;
    private int maxWorkingMinutes;

    public String getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(String vehicleId) {
        this.vehicleId = vehicleId;
    }

    public String getHub() {
        return hub;
    }

    public void setHub(String hub) {
        this.hub = hub;
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(String vehicleType) {
        this.vehicleType = vehicleType;
    }

    public double getCapacityKg() {
        return capacityKg;
    }

    public void setCapacityKg(double capacityKg) {
        this.capacityKg = capacityKg;
    }

    public double getCostPerKmInr() {
        return costPerKmInr;
    }

    public void setCostPerKmInr(double costPerKmInr) {
        this.costPerKmInr = costPerKmInr;
    }

    public String getAvailability() {
        return availability;
    }

    public void setAvailability(String availability) {
        this.availability = availability;
    }

    public int getMaxWorkingMinutes() {
        return maxWorkingMinutes;
    }

    public void setMaxWorkingMinutes(int maxWorkingMinutes) {
        this.maxWorkingMinutes = maxWorkingMinutes;
    }
}