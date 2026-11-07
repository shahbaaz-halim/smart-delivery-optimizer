package com.logismart.repository;

import com.logismart.model.ZoneDistance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ZoneDistanceRepository {

    private final JdbcTemplate jdbcTemplate;

    public ZoneDistanceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ZoneDistance> findAll() {
        String sql = """
                SELECT from_location, to_location, distance_km
                FROM zone_distance_matrix
                ORDER BY from_location, to_location
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            ZoneDistance distance = new ZoneDistance();

            distance.setFromLocation(rs.getString("from_location"));
            distance.setToLocation(rs.getString("to_location"));
            distance.setDistanceKm(ResultSets.nullableDouble(rs, "distance_km"));

            return distance;
        });
    }
}