package org.unicam.intermediate.models.dto.websocket;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GpsResponse {
    private String type;
    private boolean success;
    private String message;
    private Object data;
    private Instant timestamp;
    
    public static GpsResponse success(String type, String message, Object data) {
        return new GpsResponse(type, true, message, data, Instant.now());
    }
    
    public static GpsResponse error(String type, String message) {
        return new GpsResponse(type, false, message, null, Instant.now());
    }
    
    public static GpsResponse ack(String originalType) {
        return new GpsResponse("ACK_" + originalType, true, "Acknowledged", null, Instant.now());
    }
}