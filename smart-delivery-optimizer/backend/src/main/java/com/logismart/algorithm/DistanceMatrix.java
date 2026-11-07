package com.logismart.algorithm;

import com.logismart.model.ZoneDistance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * All-pairs shortest-path distance matrix over the hub/zone network.
 *
 * <p>Data structures:
 * <ul>
 *   <li>{@code HashMap<String,Integer>} maps a location name to a dense
 *       array index - O(1) lookup.</li>
 *   <li>{@code double[][]} stores the shortest distance between every pair
 *       of locations - O(1) query after pre-computation.</li>
 * </ul>
 *
 * <p>The supplied matrix is treated as a weighted directed graph (one edge per
 * cell). Floyd-Warshall is run once, O(V^3) with V = 12 locations
 * (about 1,700 relaxations), after which every distance lookup is O(1).
 * This replaces v1.0, which ran Dijkstra for every insertion check.
 * Floyd-Warshall also guarantees the triangle inequality holds, which the
 * insertion heuristic relies on.
 */
public final class DistanceMatrix {

    private final Map<String, Integer> index;
    private final List<String> locations;
    private final double[][] shortest;

    private DistanceMatrix(
            Map<String, Integer> index,
            List<String> locations,
            double[][] shortest) {

        this.index = index;
        this.locations = locations;
        this.shortest = shortest;
    }

    public static DistanceMatrix fromEdges(List<ZoneDistance> edges) {

        Map<String, Integer> index = new HashMap<>();
        List<String> locations = new ArrayList<>();

        for (ZoneDistance edge : edges) {
            register(edge.getFromLocation(), index, locations);
            register(edge.getToLocation(), index, locations);
        }

        int n = locations.size();
        double[][] dist = new double[n][n];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                dist[i][j] = i == j ? 0.0 : Double.POSITIVE_INFINITY;
            }
        }

        for (ZoneDistance edge : edges) {

            Integer from = index.get(key(edge.getFromLocation()));
            Integer to = index.get(key(edge.getToLocation()));
            double km = edge.getDistanceKm();

            // Invalid edges (negative, NaN) are ignored here; the
            // DataValidator reports them separately.
            if (from == null || to == null
                    || Double.isNaN(km) || km < 0) {
                continue;
            }

            dist[from][to] = Math.min(dist[from][to], km);
        }

        // Floyd-Warshall
        for (int k = 0; k < n; k++) {
            for (int i = 0; i < n; i++) {
                if (Double.isInfinite(dist[i][k])) {
                    continue;
                }
                for (int j = 0; j < n; j++) {
                    double viaK = dist[i][k] + dist[k][j];
                    if (viaK < dist[i][j]) {
                        dist[i][j] = viaK;
                    }
                }
            }
        }

        return new DistanceMatrix(
                index,
                Collections.unmodifiableList(locations),
                dist);
    }

    private static void register(
            String name,
            Map<String, Integer> index,
            List<String> locations) {

        if (name == null || name.isBlank()) {
            return;
        }

        String k = key(name);

        if (!index.containsKey(k)) {
            index.put(k, locations.size());
            locations.add(name.trim());
        }
    }

    private static String key(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Shortest distance in km, or {@code +Infinity} when either location is
     * unknown or no path exists.
     */
    public double distance(String from, String to) {

        Integer i = index.get(key(from));
        Integer j = index.get(key(to));

        if (i == null || j == null) {
            return Double.POSITIVE_INFINITY;
        }

        return shortest[i][j];
    }

    public boolean contains(String location) {
        return index.containsKey(key(location));
    }

    public boolean isReachable(String from, String to) {
        return !Double.isInfinite(distance(from, to));
    }

    public List<String> getLocations() {
        return locations;
    }

    public Set<String> locationKeys() {
        return Collections.unmodifiableSet(index.keySet());
    }

    public int size() {
        return locations.size();
    }
}
