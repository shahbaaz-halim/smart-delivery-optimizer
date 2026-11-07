package com.logismart.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Serializable data-quality report returned by the validation endpoints.
 */
public class ValidationReport {

    private final List<ValidationIssue> issues = new ArrayList<>();

    private int totalOrders;
    private int validOrders;
    private int totalVehicles;
    private int validVehicles;
    private int totalDistanceRecords;
    private int validDistanceRecords;

    void add(ValidationIssue issue) {
        issues.add(issue);
    }

    public List<ValidationIssue> getIssues() {
        return issues;
    }

    public int getErrorCount() {
        return (int) issues.stream()
                .filter(i -> i.severity() == ValidationIssue.Severity.ERROR)
                .count();
    }

    public int getWarningCount() {
        return (int) issues.stream()
                .filter(i -> i.severity() == ValidationIssue.Severity.WARNING)
                .count();
    }

    public boolean isValid() {
        return getErrorCount() == 0;
    }

    /** Issue counts grouped by rule code (sorted for stable output). */
    public Map<String, Integer> getIssueCountsByRule() {
        Map<String, Integer> counts = new TreeMap<>();
        issues.forEach(i -> counts.merge(i.rule(), 1, Integer::sum));
        return counts;
    }

    public int getTotalOrders() {
        return totalOrders;
    }

    void setTotalOrders(int totalOrders) {
        this.totalOrders = totalOrders;
    }

    public int getValidOrders() {
        return validOrders;
    }

    void setValidOrders(int validOrders) {
        this.validOrders = validOrders;
    }

    public int getInvalidOrders() {
        return totalOrders - validOrders;
    }

    public int getTotalVehicles() {
        return totalVehicles;
    }

    void setTotalVehicles(int totalVehicles) {
        this.totalVehicles = totalVehicles;
    }

    public int getValidVehicles() {
        return validVehicles;
    }

    void setValidVehicles(int validVehicles) {
        this.validVehicles = validVehicles;
    }

    public int getTotalDistanceRecords() {
        return totalDistanceRecords;
    }

    void setTotalDistanceRecords(int totalDistanceRecords) {
        this.totalDistanceRecords = totalDistanceRecords;
    }

    public int getValidDistanceRecords() {
        return validDistanceRecords;
    }

    void setValidDistanceRecords(int validDistanceRecords) {
        this.validDistanceRecords = validDistanceRecords;
    }
}
