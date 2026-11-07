package com.logismart.repository;

import com.logismart.model.DeliveryOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class DeliveryOrderRepository {

    private final JdbcTemplate jdbcTemplate;

    public DeliveryOrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DeliveryOrder> findAll() {
        String sql = """
                SELECT order_id, origin_hub, destination_zone, distance_km,
                       package_weight_kg, priority, delivery_window,
                       required_vehicle_type, service_time_min,
                       existing_planned_distance_km,
                       existing_estimated_time_min, delay_min,
                       delivery_status
                FROM delivery_orders
                ORDER BY order_id
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            DeliveryOrder order = new DeliveryOrder();

            order.setOrderId(rs.getString("order_id"));
            order.setOriginHub(rs.getString("origin_hub"));
            order.setDestinationZone(rs.getString("destination_zone"));
            order.setDistanceKm(ResultSets.nullableDouble(rs, "distance_km"));
            order.setPackageWeightKg(ResultSets.nullableDouble(rs, "package_weight_kg"));
            order.setPriority(rs.getString("priority"));
            order.setDeliveryWindow(rs.getString("delivery_window"));
            order.setRequiredVehicleType(rs.getString("required_vehicle_type"));
            order.setServiceTimeMin(ResultSets.nullableDouble(rs, "service_time_min"));
            order.setExistingPlannedDistanceKm(
                    ResultSets.nullableDouble(rs, "existing_planned_distance_km"));
            order.setExistingEstimatedTimeMin(
                    ResultSets.nullableDouble(rs, "existing_estimated_time_min"));
            order.setDelayMin(ResultSets.nullableDouble(rs, "delay_min"));
            order.setDeliveryStatus(rs.getString("delivery_status"));

            return order;
        });
    }
}