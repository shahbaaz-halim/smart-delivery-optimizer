package com.logismart.repository;

import java.sql.ResultSet;
import java.sql.SQLException;

/** JDBC helpers. */
final class ResultSets {

    private ResultSets() {
    }

    /**
     * {@link ResultSet#getDouble} returns 0 for SQL NULL, which would hide
     * missing data. This returns {@code NaN} instead so the validator can
     * report the value as missing.
     */
    static double nullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? Double.NaN : value;
    }
}
