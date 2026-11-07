package com.logismart.algorithm;

import java.util.ArrayList;
import java.util.List;

/**
 * User-controllable optimization settings (exposed through
 * {@code POST /api/optimization/run} and the Optimize screen).
 *
 * <p>The toggles also make an ablation study possible: switching
 * {@code multiTrip} and {@code allowVehicleUpgrade} off approximates the
 * v1.0 behaviour (one trip per vehicle per day, exact vehicle-type match).
 */
public class OptimizationParameters {

    public enum Objective {
        /** Minimise kilometres travelled. */
        DISTANCE,
        /** Minimise operating cost (km x vehicle cost per km). */
        COST
    }

    private double averageSpeedKmph = 40.0;
    private String dayStartTime = "08:00";
    private double reloadTimeMin = 15.0;
    private boolean multiTrip = true;
    private boolean allowVehicleUpgrade = true;
    private boolean enforceTimeWindows = true;
    private boolean twoOpt = true;
    private Objective objective = Objective.COST;
    private int maxPlanningDays = 100;

    public static OptimizationParameters defaults() {
        return new OptimizationParameters();
    }

    /** Returns a list of human-readable problems; empty when valid. */
    public List<String> validate() {

        List<String> errors = new ArrayList<>();

        if (!(averageSpeedKmph > 0 && averageSpeedKmph <= 120)) {
            errors.add("averageSpeedKmph must be between 0 (exclusive) and 120");
        }

        if (dayStartMinutes() < 0) {
            errors.add("dayStartTime must use HH:MM format, e.g. 08:00");
        }

        if (!(reloadTimeMin >= 0 && reloadTimeMin <= 240)) {
            errors.add("reloadTimeMin must be between 0 and 240");
        }

        if (maxPlanningDays < 1 || maxPlanningDays > 365) {
            errors.add("maxPlanningDays must be between 1 and 365");
        }

        if (objective == null) {
            errors.add("objective must be DISTANCE or COST");
        }

        return errors;
    }

    /** Day start in minutes from midnight, or -1 if malformed. */
    public int dayStartMinutes() {

        if (dayStartTime == null
                || !dayStartTime.trim().matches("\\d{1,2}:\\d{2}")) {
            return -1;
        }

        String[] parts = dayStartTime.trim().split(":");
        int h = Integer.parseInt(parts[0]);
        int m = Integer.parseInt(parts[1]);

        if (h > 23 || m > 59) {
            return -1;
        }

        return h * 60 + m;
    }

    public double getAverageSpeedKmph() {
        return averageSpeedKmph;
    }

    public void setAverageSpeedKmph(double averageSpeedKmph) {
        this.averageSpeedKmph = averageSpeedKmph;
    }

    public String getDayStartTime() {
        return dayStartTime;
    }

    public void setDayStartTime(String dayStartTime) {
        this.dayStartTime = dayStartTime;
    }

    public double getReloadTimeMin() {
        return reloadTimeMin;
    }

    public void setReloadTimeMin(double reloadTimeMin) {
        this.reloadTimeMin = reloadTimeMin;
    }

    public boolean isMultiTrip() {
        return multiTrip;
    }

    public void setMultiTrip(boolean multiTrip) {
        this.multiTrip = multiTrip;
    }

    public boolean isAllowVehicleUpgrade() {
        return allowVehicleUpgrade;
    }

    public void setAllowVehicleUpgrade(boolean allowVehicleUpgrade) {
        this.allowVehicleUpgrade = allowVehicleUpgrade;
    }

    public boolean isEnforceTimeWindows() {
        return enforceTimeWindows;
    }

    public void setEnforceTimeWindows(boolean enforceTimeWindows) {
        this.enforceTimeWindows = enforceTimeWindows;
    }

    public boolean isTwoOpt() {
        return twoOpt;
    }

    public void setTwoOpt(boolean twoOpt) {
        this.twoOpt = twoOpt;
    }

    public Objective getObjective() {
        return objective;
    }

    public void setObjective(Objective objective) {
        this.objective = objective;
    }

    public int getMaxPlanningDays() {
        return maxPlanningDays;
    }

    public void setMaxPlanningDays(int maxPlanningDays) {
        this.maxPlanningDays = maxPlanningDays;
    }
}
