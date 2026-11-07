package com.logismart.algorithm;

import com.logismart.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.logismart.TestData.edge;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistanceMatrixTest {

    @Test
    @DisplayName("TC-DM-01 direct distances are returned and the diagonal is 0")
    void directDistances() {
        DistanceMatrix m = DistanceMatrix.fromEdges(TestData.smallNetwork());
        assertEquals(10.0, m.distance("Hub", "Zone A"), 1e-9);
        assertEquals(0.0, m.distance("Zone B", "Zone B"), 1e-9);
        assertEquals(4, m.size());
    }

    @Test
    @DisplayName("TC-DM-02 Floyd-Warshall finds a shorter indirect path")
    void indirectShorterPath() {
        // Direct Hub->C = 40, but Hub->B->C = 10 + 30 = 40, Hub->A->C = 45; make direct worse
        List<com.logismart.model.ZoneDistance> edges = List.of(
                edge("Hub", "C", 100), edge("Hub", "B", 10), edge("B", "C", 20));
        DistanceMatrix m = DistanceMatrix.fromEdges(edges);
        assertEquals(30.0, m.distance("Hub", "C"), 1e-9);
    }

    @Test
    @DisplayName("TC-DM-03 lookup is case- and whitespace-insensitive")
    void caseInsensitive() {
        DistanceMatrix m = DistanceMatrix.fromEdges(TestData.smallNetwork());
        assertEquals(5.0, m.distance(" zone a ", "ZONE B"), 1e-9);
    }

    @Test
    @DisplayName("TC-DM-04 unknown or unreachable locations return infinity")
    void unknownAndUnreachable() {
        DistanceMatrix m = DistanceMatrix.fromEdges(List.of(edge("X", "Y", 5)));
        assertTrue(Double.isInfinite(m.distance("X", "Nowhere")));
        assertTrue(Double.isInfinite(m.distance("Y", "X")));   // one-way edge only
        assertFalse(m.isReachable("Y", "X"));
        assertTrue(m.isReachable("X", "Y"));
    }

    @Test
    @DisplayName("TC-DM-05 negative and NaN edges are ignored")
    void invalidEdgesIgnored() {
        DistanceMatrix m = DistanceMatrix.fromEdges(List.of(
                edge("A", "B", -5), edge("A", "C", Double.NaN), edge("A", "D", 7)));
        assertTrue(Double.isInfinite(m.distance("A", "B")));
        assertTrue(Double.isInfinite(m.distance("A", "C")));
        assertEquals(7.0, m.distance("A", "D"), 1e-9);
    }
}
