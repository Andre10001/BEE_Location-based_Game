package org.unicam.intermediate.service.environmental;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.unicam.intermediate.events.ParticipantPositionChangedEvent;
import org.unicam.intermediate.models.pojo.Participant;
import org.unicam.intermediate.models.pojo.PhysicalPlace;
import org.unicam.intermediate.service.participant.ParticipantDataService;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class OccupancySyncService {

    private static final String OCCUPIED_ATTRIBUTE = "occupied";
    private static final long OCCUPANCY_UPDATE_DELAY_MS = 5_000L;

    private final EnvironmentDataService environmentDataService;
    private final ParticipantDataService participantDataService;

    // placeId -> dueEpochMillis
    private final Map<String, Long> pendingPlaceUpdates = new ConcurrentHashMap<>();

    @EventListener
    public void onParticipantPositionChanged(ParticipantPositionChangedEvent event) {
        if (event == null) {
            return;
        }

        String oldPosition = trimToNull(event.oldPosition());
        String newPosition = trimToNull(event.newPosition());

        if (Objects.equals(oldPosition, newPosition)) {
            return;
        }

        long dueAt = System.currentTimeMillis() + OCCUPANCY_UPDATE_DELAY_MS;
        schedulePlaceUpdate(oldPosition, dueAt);
        schedulePlaceUpdate(newPosition, dueAt);
    }

    @Scheduled(fixedRate = 1000)
    public void processPendingOccupancyUpdates() {
        long now = System.currentTimeMillis();

        for (Map.Entry<String, Long> entry : pendingPlaceUpdates.entrySet()) {
            if (entry.getValue() > now) {
                continue;
            }

            String placeId = entry.getKey();
            if (!pendingPlaceUpdates.remove(placeId, entry.getValue())) {
                continue;
            }

            recalculateAndApplyOccupancy(placeId);
        }
    }

    private void schedulePlaceUpdate(String placeId, long dueAt) {
        if (placeId == null) {
            return;
        }

        pendingPlaceUpdates.merge(placeId, dueAt, Math::max);
    }

    private void recalculateAndApplyOccupancy(String placeId) {
        Optional<PhysicalPlace> placeOpt = environmentDataService.getPhysicalPlace(placeId);
        if (placeOpt.isEmpty()) {
            return;
        }

        PhysicalPlace place = placeOpt.get();
        Map<String, Object> attributes = place.getAttributes();

        // Update only if occupancy is explicitly modeled in the current environment.
        if (attributes == null || !attributes.containsKey(OCCUPIED_ATTRIBUTE)) {
            return;
        }

        boolean occupied = participantDataService.getParticipants().stream()
                .map(Participant::getPosition)
                .anyMatch(placeId::equals);

        Object previousValue = attributes.put(OCCUPIED_ATTRIBUTE, occupied);
        if (!Objects.equals(asBoolean(previousValue), occupied)) {
            log.info("[OccupancySync] Updated {}.{}: {} -> {}",
                    placeId, OCCUPIED_ATTRIBUTE, previousValue, occupied);
        } else {
            log.debug("[OccupancySync] {}.{} unchanged at {}",
                    placeId, OCCUPIED_ATTRIBUTE, occupied);
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.equalsIgnoreCase("true")) {
            return true;
        }
        if (text.equalsIgnoreCase("false")) {
            return false;
        }
        return null;
    }
}
