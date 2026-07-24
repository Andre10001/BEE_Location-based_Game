package org.unicam.intermediate.service.environmental;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.RuntimeService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.unicam.intermediate.models.WaitingBinding;
import org.unicam.intermediate.models.environmental.LocationUpdateEvent;
import org.unicam.intermediate.models.pojo.PhysicalPlace;
import org.unicam.intermediate.models.record.MovementResponse;
import org.unicam.intermediate.service.environmental.movement.GpsProcessingService;
import org.unicam.intermediate.service.participant.ParticipantPositionService;
import org.unicam.intermediate.service.participant.UserParticipantMappingService;
import org.unicam.intermediate.service.task.TaskTrackingService;

import java.time.Instant;
import java.util.*;

@Service
@Slf4j
@AllArgsConstructor
public class LocationEventService {

    private final ApplicationEventPublisher eventPublisher;
    private final EnvironmentDataService environmentDataService;
    private final ParticipantPositionService positionService;
    private final UserParticipantMappingService userParticipantMapping;
    private final TaskTrackingService taskTrackingService;
    private final GpsProcessingService gpsProcessingService;
    private final BindingService bindingService;
    private final ProximityService proximityService;
    private final BindingProximityMonitor bindingProximityMonitor;

    /**
     * Publish location update and let the engine handle it
     */
    public Map<String, Object> publishLocationEvent(String userId, String participantId,
                                                    String businessKey, double lat, double lon) {
        Map<String, Object> result = new HashMap<>();
        List<String> triggeredEvents = new ArrayList<>();

        // 1. Determine which place the location is in
        Optional<PhysicalPlace> currentPlace = environmentDataService.findPhysicalPlaceContainingLocation(lat, lon);
        String placeId = currentPlace.map(PhysicalPlace::getId).orElse(null);
        String placeName = currentPlace.map(PhysicalPlace::getName).orElse("Unknown");

        // 2. Update position (using existing service)
        positionService.updatePosition(participantId, lat, lon, placeId);

        // 3. Create and publish event
        LocationUpdateEvent event = new LocationUpdateEvent(
                this,
                userId,
                participantId,
                businessKey,
                lat,
                lon,
                placeId,
                placeName,
                Instant.now()
        );

        eventPublisher.publishEvent(event);
        triggeredEvents.add("LOCATION_UPDATE");

        // 4. Process movement tasks using EXISTING service
        MovementResponse movementResponse = gpsProcessingService.processUserLocation(userId, lat, lon);
        if (movementResponse.success()) {
            triggeredEvents.add("MOVEMENT_COMPLETED");
            result.put("movementDestination", movementResponse.destination());
            result.put("movementMessage", movementResponse.message());
        }

        // 5. Check for binding/unbinding readiness using EXISTING logic
        if (businessKey != null && participantId != null) {
            Map<String, Object> bindingStatus = checkBindingStatusUsingExistingServices(
                    participantId, businessKey);
            result.put("bindingStatus", bindingStatus);

            if (!((List<?>) bindingStatus.get("bindingReady")).isEmpty()) {
                triggeredEvents.add("BINDING_READY");
            }
            if (!((List<?>) bindingStatus.get("unbindingReady")).isEmpty()) {
                triggeredEvents.add("UNBINDING_READY");
            }
        }

        result.put("placeId", placeId);
        result.put("placeName", placeName);
        result.put("triggeredEvents", triggeredEvents);
        result.put("movementSuccess", movementResponse.success());

        log.info("[LocationEvent] Published location event for {} at place {} - triggered: {}",
                participantId, placeName, triggeredEvents);

        return result;
    }

    /**
     * Check binding/unbinding status using existing services
     */
    private Map<String, Object> checkBindingStatusUsingExistingServices(String participantId, String businessKey) {
        Map<String, Object> status = new HashMap<>();
        List<String> bindingReady = new ArrayList<>();
        List<String> unbindingReady = new ArrayList<>();
        List<String> waitingFor = new ArrayList<>();

        // Use EXISTING bindingService to get waiting bindings
        List<WaitingBinding> allWaitingBindings = bindingService.getAllWaitingBindings();
        List<WaitingBinding> relevantBindings = allWaitingBindings.stream()
                .filter(wb -> wb.getBusinessKey().equals(businessKey))
                .filter(wb -> wb.getCurrentParticipantId().equals(participantId) ||
                        wb.getTargetParticipantId().equals(participantId))
                .toList();

        for (WaitingBinding wb : relevantBindings) {
            String otherParticipant = wb.getCurrentParticipantId().equals(participantId)
                    ? wb.getTargetParticipantId()
                    : wb.getCurrentParticipantId();

            // Use EXISTING proximityService
            PhysicalPlace bindingPlace = proximityService.getBindingPlace(
                    wb.getCurrentParticipantId(), wb.getTargetParticipantId());

            if (bindingPlace != null) {
                bindingReady.add(otherParticipant);
                log.info("[LocationEvent] Binding ready between {} and {} at {}",
                        participantId, otherParticipant, bindingPlace.getName());
            } else {
                waitingFor.add(otherParticipant);
            }
        }

        // Check unbindings using EXISTING service
        List<WaitingBinding> allWaitingUnbindings = bindingService.getAllWaitingUnbindings();
        List<WaitingBinding> relevantUnbindings = allWaitingUnbindings.stream()
                .filter(wu -> wu.getBusinessKey().equals(businessKey))
                .filter(wu -> wu.getCurrentParticipantId().equals(participantId) ||
                        wu.getTargetParticipantId().equals(participantId))
                .toList();

        for (WaitingBinding wu : relevantUnbindings) {
            String otherParticipant = wu.getCurrentParticipantId().equals(participantId)
                    ? wu.getTargetParticipantId()
                    : wu.getCurrentParticipantId();

            PhysicalPlace unbindingPlace = proximityService.getBindingPlace(
                    wu.getCurrentParticipantId(), wu.getTargetParticipantId());

            if (unbindingPlace != null) {
                unbindingReady.add(otherParticipant);
                log.info("[LocationEvent] Unbinding ready between {} and {} at {}",
                        participantId, otherParticipant, unbindingPlace.getName());
            }
        }

        status.put("bindingReady", bindingReady);
        status.put("unbindingReady", unbindingReady);
        status.put("waitingFor", waitingFor);
        status.put("waitingBindingsCount", relevantBindings.size());
        status.put("waitingUnbindingsCount", relevantUnbindings.size());

        return status;
    }

    /**
     * Register user for a specific task - REUSING existing service
     */
    public void registerUserForTask(String userId, String taskId) {

        Map<String, Object> trackingInfo = taskTrackingService.startTrackingForTask(taskId, userId);

        log.info("[LocationEvent] User {} registered for task {} as participant {}",
                userId, taskId, trackingInfo.get("participantId"));
    }



    /**
     * Manually trigger binding/unbinding check - delegates to EXISTING monitor
     */
    public void triggerBindingCheck() {
        bindingProximityMonitor.checkWaitingBindings();
    }
}