package org.unicam.intermediate.models.environmental;

import java.time.Instant;

public class Coordinate {
        public final double lat;  // y
        public final double lon;  // x
        public final Instant timestamp;
        public final String destination;

        public Coordinate(double lat, double lon, String destination) {
            this.lat = lat;
            this.lon = lon;
            this.destination = destination;
            this.timestamp = Instant.now();
        }

        @Override
        public String toString() {
            return "Coordinate{" +
                    "lat=" + lat +
                    ", lon=" + lon +
                    ", timestamp=" + timestamp +
                    ", destination='" + destination + '\'' +
                    '}';
        }
    }