// src/main/java/org/unicam/intermediate/models/dto/websocket/GpsMessage.java

package org.unicam.intermediate.models.dto.websocket;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = GpsMessage.LocationUpdate.class, name = "LOCATION_UPDATE"),
        @JsonSubTypes.Type(value = GpsMessage.Heartbeat.class, name = "HEARTBEAT"),
        @JsonSubTypes.Type(value = GpsMessage.StartTracking.class, name = "START_TRACKING"),
        @JsonSubTypes.Type(value = GpsMessage.StopTracking.class, name = "STOP_TRACKING")
})
public abstract class GpsMessage {
    private String type;
    private Instant timestamp;

    @Data
    @NoArgsConstructor
    public static class LocationUpdate extends GpsMessage {
        private Double lat;
        private Double lon;
        private Double accuracy;
        private Double altitude;
        private Double speed;
        // Generico - non specifica il tipo di task
        private String businessKey;
        private String processInstanceId;

        public LocationUpdate(Double lat, Double lon) {
            super("LOCATION_UPDATE", Instant.now());
            this.lat = lat;
            this.lon = lon;
        }
    }

    @Data
    @NoArgsConstructor
    public static class Heartbeat extends GpsMessage {
        public Heartbeat(boolean dummy) {
            super("HEARTBEAT", Instant.now());
        }
    }

    @Data
    @NoArgsConstructor
    public static class StartTracking extends GpsMessage {
        private String businessKey;
        private Integer updateInterval; // seconds

        public StartTracking(String businessKey) {
            super("START_TRACKING", Instant.now());
            this.businessKey = businessKey;
        }
    }

    @Data
    @NoArgsConstructor
    public static class StopTracking extends GpsMessage {
        private String businessKey;

        public StopTracking(String businessKey) {
            super("STOP_TRACKING", Instant.now());
            this.businessKey = businessKey;
        }
    }
}