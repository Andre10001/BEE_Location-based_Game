// src/main/java/org/unicam/intermediate/models/environmental/LocationArea.java

package org.unicam.intermediate.models.environmental;

import java.util.List;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public class LocationArea {
    private final List<List<Double>> coordinates;
    private final double minX, maxX, minY, maxY;

    // Tolleranza per confronti floating point (importante per coordinate GPS)
    private static final double EPSILON = 1e-9;

    public LocationArea(List<List<Double>> coordinates) {
        this.coordinates = coordinates;

        this.minX = coordinates.stream().mapToDouble(c -> c.get(0)).min().orElse(0);
        this.maxX = coordinates.stream().mapToDouble(c -> c.get(0)).max().orElse(0);
        this.minY = coordinates.stream().mapToDouble(c -> c.get(1)).min().orElse(0);
        this.maxY = coordinates.stream().mapToDouble(c -> c.get(1)).max().orElse(0);

        log.debug("Created LocationArea - BBox: lon[{}, {}], lat[{}, {}]",
                minX, maxX, minY, maxY);
    }

    /**
     * Main contains method with vertex handling
     */
    public boolean contains(double lat, double lon) {
        log.trace("Checking point ({}, {}) in polygon", lat, lon);

        // 1. First check if point is exactly a vertex
        if (isVertex(lat, lon)) {
            log.debug("Point is a vertex of the polygon - returning true");
            return true;
        }

        // 2. Check if point is on an edge
        if (isOnEdge(lat, lon)) {
            log.debug("Point is on an edge of the polygon - returning true");
            return true;
        }

        // 3. Check if point is inside using winding number
        boolean inside = windingNumberTest(lat, lon);
        log.trace("Winding number test result: {}", inside);

        return inside;
    }

    /**
     * Check if point is exactly a vertex
     */
    private boolean isVertex(double lat, double lon) {
        for (List<Double> coord : coordinates) {
            double vLon = coord.get(0);
            double vLat = coord.get(1);

            if (Math.abs(vLon - lon) < EPSILON && Math.abs(vLat - lat) < EPSILON) {
                log.debug("Point matches vertex at ({}, {})", vLat, vLon);
                return true;
            }
        }
        return false;
    }

    /**
     * Check if point is on an edge of the polygon
     */
    private boolean isOnEdge(double lat, double lon) {
        int n = coordinates.size();

        for (int i = 0; i < n; i++) {
            List<Double> p1 = coordinates.get(i);
            List<Double> p2 = coordinates.get((i + 1) % n);

            double x1 = p1.get(0); // lon1
            double y1 = p1.get(1); // lat1
            double x2 = p2.get(0); // lon2
            double y2 = p2.get(1); // lat2

            // Check if point is on the line segment
            double crossProduct = (lat - y1) * (x2 - x1) - (lon - x1) * (y2 - y1);

            if (Math.abs(crossProduct) < EPSILON) {
                // Point is on the line, check if it's within the segment
                if (Math.min(x1, x2) <= lon && lon <= Math.max(x1, x2) &&
                        Math.min(y1, y2) <= lat && lat <= Math.max(y1, y2)) {
                    log.debug("Point is on edge between ({}, {}) and ({}, {})",
                            y1, x1, y2, x2);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Winding number algorithm for point-in-polygon test
     */
    private boolean windingNumberTest(double lat, double lon) {
        int wn = 0;
        int n = coordinates.size();

        for (int i = 0; i < n; i++) {
            List<Double> p1 = coordinates.get(i);
            List<Double> p2 = coordinates.get((i + 1) % n);

            double x1 = p1.get(0); // lon
            double y1 = p1.get(1); // lat
            double x2 = p2.get(0);
            double y2 = p2.get(1);

            if (y1 <= lat) {
                if (y2 > lat) { // upward crossing
                    if (isLeft(x1, y1, x2, y2, lon, lat) > 0) {
                        wn++;
                    }
                }
            } else {
                if (y2 <= lat) { // downward crossing
                    if (isLeft(x1, y1, x2, y2, lon, lat) < 0) {
                        wn--;
                    }
                }
            }
        }

        return wn != 0;
    }

    /**
     * Test if point is left/on/right of an infinite line
     */
    private double isLeft(double x0, double y0, double x1, double y1, double px, double py) {
        return ((x1 - x0) * (py - y0) - (px - x0) * (y1 - y0));
    }

    public void debugContains(double lat, double lon) {
        log.info("=====================================");
        log.info("DEBUG: Testing point ({}, {})", lat, lon);
        log.info("Polygon has {} vertices:", coordinates.size());

        for (int i = 0; i < coordinates.size(); i++) {
            List<Double> coord = coordinates.get(i);
            log.info("  Vertex {}: ({}, {})", i, coord.get(1), coord.get(0));

            // Check exact match
            if (coord.get(0).equals(lon) && coord.get(1).equals(lat)) {
                log.info("  ⚠️ EXACT MATCH with vertex {}!", i);
            }

            // Check with epsilon
            double dLon = Math.abs(coord.get(0) - lon);
            double dLat = Math.abs(coord.get(1) - lat);
            if (dLon < EPSILON && dLat < EPSILON) {
                log.info("  ✓ EPSILON MATCH with vertex {} (dLon={}, dLat={})", i, dLon, dLat);
            }
        }

        log.info("Bounding box: lon[{}, {}], lat[{}, {}]", minX, maxX, minY, maxY);

        boolean inBBox = lon >= minX && lon <= maxX && lat >= minY && lat <= maxY;
        boolean isVert = isVertex(lat, lon);
        boolean onEdge = isOnEdge(lat, lon);
        boolean windingTest = windingNumberTest(lat, lon);

        log.info("Results:");
        log.info("  In bounding box: {}", inBBox);
        log.info("  Is vertex: {}", isVert);
        log.info("  On edge: {}", onEdge);
        log.info("  Winding test: {}", windingTest);
        log.info("  FINAL (contains): {}", contains(lat, lon));
        log.info("=====================================");
    }

}