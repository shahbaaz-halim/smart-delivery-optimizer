package com.logismart.validation;

/**
 * One data-quality finding.
 *
 * @param severity ERROR (record excluded from optimization) or WARNING (record kept)
 * @param entity   ORDER, VEHICLE or DISTANCE
 * @param recordId order/vehicle id, "From -> To" for distances, or "row N" when the id is missing
 * @param field    source column name, e.g. Package_Weight_Kg
 * @param rule     stable rule code, e.g. WEIGHT_NON_POSITIVE
 * @param value    offending value as text (may be empty)
 * @param message  human-readable explanation
 */
public record ValidationIssue(
        Severity severity,
        String entity,
        String recordId,
        String field,
        String rule,
        String value,
        String message) {

    public enum Severity {
        ERROR,
        WARNING
    }
}
