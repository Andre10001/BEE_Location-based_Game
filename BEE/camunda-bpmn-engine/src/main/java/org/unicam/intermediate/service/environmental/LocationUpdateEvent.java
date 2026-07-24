// src/main/java/org/unicam/intermediate/models/environmental/LocationUpdateEvent.java

package org.unicam.intermediate.models.environmental;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.Instant;

@Getter
public class LocationUpdateEvent extends ApplicationEvent {

    private final String userId;
    private final String participantId;
    private final String businessKey;
    private final double lat;
    private final double lon;
    private final String placeId;
    private final String placeName;
    private final Instant occurredAt;

    public LocationUpdateEvent(Object source, String userId, String participantId,
                               String businessKey, double lat, double lon,
                               String placeId, String placeName, Instant occurredAt) {
        super(source);
        this.userId = userId;
        this.participantId = participantId;
        this.businessKey = businessKey;
        this.lat = lat;
        this.lon = lon;
        this.placeId = placeId;
        this.placeName = placeName;
        this.occurredAt = occurredAt;
    }
}