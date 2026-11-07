package com.logismart;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end API tests: starts the real application on a random port in
 * CSV mode (no database needed) and calls it over HTTP.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.data-source=csv",
                "management.health.db.enabled=false"
        })
class SmartDeliveryOptimizerApplicationTests {

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String contentType, String body) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .header("Content-Type", contentType)
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("TC-API-01 health endpoint reports UP")
    void health() throws Exception {
        HttpResponse<String> r = get("/actuator/health");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"UP\""));
    }

    @Test
    @DisplayName("TC-API-02 summary returns KPI rows with computed status")
    void summary() throws Exception {
        HttpResponse<String> r = get("/api/optimization/summary");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"kpis\""));
        assertTrue(r.body().contains("\"status\":\"IMPROVED\""));
    }

    @Test
    @DisplayName("TC-API-03 run accepts parameters and rejects invalid ones with 400")
    void runWithParameters() throws Exception {
        HttpResponse<String> ok = post("/api/optimization/run", "application/json",
                "{\"averageSpeedKmph\":35,\"objective\":\"DISTANCE\"}");
        assertEquals(200, ok.statusCode());
        assertTrue(ok.body().contains("\"ordersAllocated\""));

        HttpResponse<String> bad = post("/api/optimization/run", "application/json",
                "{\"averageSpeedKmph\":-5}");
        assertEquals(400, bad.statusCode());
        assertTrue(bad.body().contains("averageSpeedKmph"));

        HttpResponse<String> malformed = post("/api/optimization/run", "application/json",
                "{\"objective\":\"FASTEST\"}");
        assertEquals(400, malformed.statusCode());
    }

    @Test
    @DisplayName("TC-API-04 uploading an invalid orders CSV returns a validation report")
    void validateUpload() throws Exception {
        String csv = String.join(",", com.logismart.data.CsvParser.ORDER_COLUMNS) + "\n"
                + "ORD-1,North Hub,Zone A,18,5,High,08:00-11:00,Bike,10,20,40,0,On Time\n"
                + "ORD-2,North Hub,Zone Q,18,-5,Urgent,later,Van,10,20,40,0,On Time\n";
        HttpResponse<String> r = post("/api/validation/orders", "text/csv", csv);
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"validOrders\":1"));
        assertTrue(r.body().contains("ZONE_UNKNOWN"));
        assertTrue(r.body().contains("PRIORITY_INVALID"));

        HttpResponse<String> badHeader = post("/api/validation/orders", "text/csv", "a,b\n1,2\n");
        assertEquals(400, badHeader.statusCode());
    }

    @Test
    @DisplayName("TC-API-05 read endpoints respond (routes, unallocated, validation, utilisation)")
    void readEndpoints() throws Exception {
        for (String path : new String[]{
                "/api/orders", "/api/vehicles", "/api/distances",
                "/api/optimization/routes", "/api/optimization/unallocated",
                "/api/optimization/unallocated/reasons", "/api/optimization/daily-utilization",
                "/api/optimization/daily-type-utilization", "/api/optimization/run",
                "/api/validation/report", "/api/optimization/parameters/defaults"}) {
            assertEquals(200, get(path).statusCode(), path);
        }
    }
}
