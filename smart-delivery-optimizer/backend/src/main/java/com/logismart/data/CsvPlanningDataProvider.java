package com.logismart.data;

import com.logismart.model.DeliveryOrder;
import com.logismart.model.Vehicle;
import com.logismart.model.ZoneDistance;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;

/**
 * Loads planning data from CSV files: {@code orders.csv},
 * {@code vehicles.csv} and {@code zone_distance_matrix.csv}.
 * Location defaults to the copies bundled in the jar.
 */
@Component
@ConditionalOnProperty(name = "app.data-source", havingValue = "csv")
public class CsvPlanningDataProvider implements PlanningDataProvider {

    private final String location;
    private final ResourceLoader loader = new DefaultResourceLoader();

    public CsvPlanningDataProvider(
            @Value("${app.csv.location:classpath:data/}") String location) {

        this.location = location.endsWith("/") ? location : location + "/";
    }

    @Override
    public List<DeliveryOrder> loadOrders() {
        return read("orders.csv", r -> CsvParser.parseOrders(r).orders());
    }

    @Override
    public List<Vehicle> loadVehicles() {
        return read("vehicles.csv", CsvParser::parseVehicles);
    }

    @Override
    public List<ZoneDistance> loadDistances() {
        return read("zone_distance_matrix.csv", CsvParser::parseDistances);
    }

    @Override
    public String sourceName() {
        return "CSV (" + location + ")";
    }

    private <T> T read(String file, Function<Reader, T> parser) {

        Resource resource = loader.getResource(location + file);

        try (Reader reader = new InputStreamReader(
                resource.getInputStream(), StandardCharsets.UTF_8)) {
            return parser.apply(reader);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + location + file, e);
        }
    }
}
