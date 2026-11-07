package com.logismart.kpi;

/**
 * One baseline-vs-optimized KPI comparison row.
 *
 * @param key                stable identifier, e.g. "total_distance"
 * @param name               display name
 * @param unit               "km", "min", "INR", "%", "days", "orders" ...
 * @param direction          HIGHER_IS_BETTER, LOWER_IS_BETTER or NEUTRAL
 * @param baseline           baseline value
 * @param optimized          optimized value
 * @param changePercent      raw change (optimized - baseline) / baseline x 100; null if baseline is 0
 * @param improvementPercent change signed so that positive always means "better"; null if not applicable
 * @param status             IMPROVED, WORSENED, UNCHANGED or INFO - derived from the numbers
 * @param baselineDefinition how the baseline value is calculated
 * @param optimizedDefinition how the optimized value is calculated
 * @param interpretation     plain-language reading of this result
 */
public record KpiRow(
        String key,
        String name,
        String unit,
        Direction direction,
        double baseline,
        double optimized,
        Double changePercent,
        Double improvementPercent,
        Status status,
        String baselineDefinition,
        String optimizedDefinition,
        String interpretation) {

    public enum Direction {
        HIGHER_IS_BETTER,
        LOWER_IS_BETTER,
        NEUTRAL
    }

    public enum Status {
        IMPROVED,
        WORSENED,
        UNCHANGED,
        INFO
    }
}
