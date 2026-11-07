package com.logismart.model;

public class DeliveryOrder {

    private String orderId;
    private String originHub;
    private String destinationZone;
    private double distanceKm;
    private double packageWeightKg;
    private String priority;
    private String deliveryWindow;
    private String requiredVehicleType;
    private double serviceTimeMin;
    private double existingPlannedDistanceKm;
    private double existingEstimatedTimeMin;
    private double delayMin;
    private String deliveryStatus;
    private String allocationReason;

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getOriginHub() {
        return originHub;
    }

    public void setOriginHub(String originHub) {
        this.originHub = originHub;
    }

    public String getDestinationZone() {
        return destinationZone;
    }

    public void setDestinationZone(String destinationZone) {
        this.destinationZone = destinationZone;
    }

    public double getDistanceKm() {
        return distanceKm;
    }

    public void setDistanceKm(double distanceKm) {
        this.distanceKm = distanceKm;
    }

    public double getPackageWeightKg() {
        return packageWeightKg;
    }

    public void setPackageWeightKg(double packageWeightKg) {
        this.packageWeightKg = packageWeightKg;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getDeliveryWindow() {
        return deliveryWindow;
    }

    public void setDeliveryWindow(String deliveryWindow) {
        this.deliveryWindow = deliveryWindow;
    }

    public String getRequiredVehicleType() {
        return requiredVehicleType;
    }

    public void setRequiredVehicleType(String requiredVehicleType) {
        this.requiredVehicleType = requiredVehicleType;
    }

    public double getServiceTimeMin() {
        return serviceTimeMin;
    }

    public void setServiceTimeMin(double serviceTimeMin) {
        this.serviceTimeMin = serviceTimeMin;
    }

    public double getExistingPlannedDistanceKm() {
        return existingPlannedDistanceKm;
    }

    public void setExistingPlannedDistanceKm(
            double existingPlannedDistanceKm) {
        this.existingPlannedDistanceKm =
                existingPlannedDistanceKm;
    }

    public double getExistingEstimatedTimeMin() {
        return existingEstimatedTimeMin;
    }

    public void setExistingEstimatedTimeMin(
            double existingEstimatedTimeMin) {
        this.existingEstimatedTimeMin =
                existingEstimatedTimeMin;
    }

    public double getDelayMin() {
        return delayMin;
    }

    public void setDelayMin(double delayMin) {
        this.delayMin = delayMin;
    }

    public String getDeliveryStatus() {
        return deliveryStatus;
    }

    public void setDeliveryStatus(String deliveryStatus) {
        this.deliveryStatus = deliveryStatus;
    }

    public String getAllocationReason() {
        return allocationReason;
    }

    public void setAllocationReason(String allocationReason) {
        this.allocationReason = allocationReason;
    }
}