package com.logismart.data;

import com.logismart.model.Vehicle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvParserTest {

    private static final String HEADER = String.join(",", CsvParser.ORDER_COLUMNS);

    @Test
    @DisplayName("TC-CSV-01 orders parse by header name, in any column order")
    void parsesOrders() {
        String csv = HEADER + "\n"
                + "ORD-1,South Hub,Zone E,17.63,23.17,Medium,14:00-17:00,Van,16.3,19.84,50.6,0,On Time\n";
        var result = CsvParser.parseOrders(new StringReader(csv));

        assertEquals(1, result.orders().size());
        assertEquals("Zone E", result.orders().get(0).getDestinationZone());
        assertEquals(23.17, result.orders().get(0).getPackageWeightKg(), 1e-9);
        assertTrue(result.rowProblems().isEmpty());
    }

    @Test
    @DisplayName("TC-CSV-02 empty and non-numeric numbers become NaN (reported later as invalid)")
    void missingNumbers() {
        String csv = HEADER + "\n"
                + "ORD-1,South Hub,,17.63,heavy,Medium,Flexible,Van,,19.84,50.6,0,On Time\n";
        var o = CsvParser.parseOrders(new StringReader(csv)).orders().get(0);

        assertTrue(Double.isNaN(o.getPackageWeightKg()));
        assertTrue(Double.isNaN(o.getServiceTimeMin()));
        assertNull(o.getDestinationZone());
    }

    @Test
    @DisplayName("TC-CSV-03 quoted fields with commas and CRLF line endings are handled")
    void quotedAndCrlf() {
        String csv = HEADER + "\r\n"
                + "\"ORD,1\",North Hub,Zone A,1,2,Low,Flexible,Bike,5,1,1,0,On Time\r\n";
        var o = CsvParser.parseOrders(new StringReader(csv)).orders().get(0);
        assertEquals("ORD,1", o.getOrderId());
    }

    @Test
    @DisplayName("TC-CSV-04 a missing required column is rejected with a clear message")
    void missingColumn() {
        String csv = "Order_ID,Origin_Hub\nORD-1,North Hub\n";
        var e = assertThrows(CsvParser.CsvFormatException.class,
                () -> CsvParser.parseOrders(new StringReader(csv)));
        assertTrue(e.getMessage().contains("Destination_Zone"));
    }

    @Test
    @DisplayName("TC-CSV-05 rows with the wrong number of columns are flagged")
    void wrongColumnCount() {
        String csv = HEADER + "\nORD-1,North Hub,Zone A\n";
        var result = CsvParser.parseOrders(new StringReader(csv));
        assertEquals(1, result.rowProblems().size());
    }

    @Test
    @DisplayName("TC-CSV-06 the bundled dataset loads completely (1,800 orders, 52 vehicles, 144 distances)")
    void bundledDataset() {
        CsvPlanningDataProvider p = new CsvPlanningDataProvider("classpath:data/");
        assertEquals(1800, p.loadOrders().size());
        List<Vehicle> vehicles = p.loadVehicles();
        assertEquals(52, vehicles.size());
        assertEquals(47, vehicles.stream().filter(v -> v.getAvailability().equals("Available")).count());
        assertEquals(144, p.loadDistances().size());
    }
}
