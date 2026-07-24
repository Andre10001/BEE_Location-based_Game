package org.unicam.intermediate.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.runtime.Execution;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.Participant;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.unicam.intermediate.models.WaitingBinding;
import org.unicam.intermediate.models.dto.websocket.GpsMessage;
import org.unicam.intermediate.models.dto.websocket.GpsResponse;
import org.unicam.intermediate.models.pojo.PhysicalPlace;
import org.unicam.intermediate.service.environmental.BindingService;
import org.unicam.intermediate.service.environmental.EnvironmentDataService;
import org.unicam.intermediate.service.environmental.LocationEventService;
import org.unicam.intermediate.service.environmental.ProximityService;
import org.unicam.intermediate.service.environmental.movement.MovementTaskRegistry;
import org.unicam.intermediate.service.participant.ParticipantPositionService;
import org.unicam.intermediate.service.participant.ParticipantService;
import org.unicam.intermediate.service.participant.UserParticipantMappingService;
import org.unicam.intermediate.service.task.TaskTrackingService;
import org.unicam.intermediate.service.websocket.WebSocketSessionManager;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class GpsWebSocketHandler extends TextWebSocketHandler {

    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper;
    private final ParticipantPositionService positionService;
    private final UserParticipantMappingService userParticipantMapping;
    private final LocationEventService locationEventService;
    private final RuntimeService runtimeService;
    private final EnvironmentDataService environmentDataService;
    private final BindingService bindingService;
    private final ProximityService proximityService;
    private final TaskService taskService;
    private final RepositoryService repositoryService;
    private final IdentityService identityService;
    private final TaskTrackingService taskTrackingService;
    private final ParticipantService participantService;
    private final MovementTaskRegistry movementTaskRegistry;

    private final ScheduledExecutorService heartbeatExecutor = Executors.newScheduledThreadPool(1);
    private final ConcurrentHashMap<String, Long> lastActivity = new ConcurrentHashMap<>();

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String userId = getUserId(session);
        String businessKey = getBusinessKey(session); // Prendi dalla sessione
        lastActivity.put(session.getId(), System.currentTimeMillis());

        try {
            String payload = message.getPayload();
            log.debug("[GPS WS] Received from {} (BK: {}): {}", userId, businessKey, payload);

            GpsMessage gpsMessage = objectMapper.readValue(payload, GpsMessage.class);

            if (gpsMessage instanceof GpsMessage.LocationUpdate location) {
                handleLocationUpdate(session, userId, businessKey, location);
            } else if (gpsMessage instanceof GpsMessage.Heartbeat) {
                handleHeartbeat(session, userId);
            } else if (gpsMessage instanceof GpsMessage.StartTracking start) {
                handleStartTracking(session, userId, start);
            } else if (gpsMessage instanceof GpsMessage.StopTracking stop) {
                handleStopTracking(session, userId, stop);
            } else {
                log.warn("[GPS WS] Unknown message type from {}: {}", userId, gpsMessage.getType());
                sendError(session, "UNKNOWN_TYPE", "Unknown message type: " + gpsMessage.getType());
            }

        } catch (Exception e) {
            log.error("[GPS WS] Error processing message from {}: {}", userId, e.getMessage(), e);
            sendError(session, "PROCESSING_ERROR", "Failed to process message: " + e.getMessage());
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        try {
            String userId = getUserId(session);
            String businessKey = getBusinessKey(session);

            if (userId == null) {
                log.error("[GPS WS] No userId in session attributes");
                session.close(CloseStatus.BAD_DATA.withReason("Missing userId"));
                return;
            }

            sessionManager.addSession(userId, session);
            lastActivity.put(session.getId(), System.currentTimeMillis());

            // AUTO-DISCOVER PARTICIPANT
            String participantId = null;
            String participantName = null;
            boolean newlyRegistered = false;

            if (businessKey != null && !businessKey.isBlank()) {
                UserParticipantMappingService.ParticipantDiscoveryResult discovery =
                        userParticipantMapping.autoDiscoverAndRegister(userId, businessKey);

                if (discovery != null) {
                    participantId = discovery.getParticipantId();
                    participantName = discovery.getParticipantName();
                    newlyRegistered = discovery.isNewlyRegistered();

                    if (newlyRegistered) {
                        log.info("[GPS WS] Auto-registered user {} as participant {} for BK {}",
                                userId, participantId, businessKey);
                    } else {
                        log.info("[GPS WS] User {} already mapped to participant {} for BK {}",
                                userId, participantId, businessKey);
                    }
                } else {
                    log.warn("[GPS WS] Could not auto-discover participant for user {} in BK {}. " +
                            "Will retry on first location update.", userId, businessKey);
                }
            }

            // Send welcome message
            Map<String, Object> statusData = new HashMap<>();
            statusData.put("sessionId", session.getId());
            statusData.put("userId", userId);
            statusData.put("businessKey", businessKey);

            if (participantId != null) {
                statusData.put("participantId", participantId);
                statusData.put("participantName", participantName);
                statusData.put("mappingStatus", newlyRegistered ? "newly_assigned" : "existing");
            } else {
                statusData.put("mappingStatus", "pending");
                statusData.put("info", "Participant will be assigned on first task interaction");
            }

            GpsResponse welcome = GpsResponse.success("CONNECTION",
                    participantId != null ?
                            "Connected as " + participantName :
                            "Connected - participant assignment pending",
                    statusData);

            sendMessage(session, welcome);

            log.info("[GPS WS] Connection established - userId: {}, participantId: {}, sessionId: {}",
                    userId, participantId, session.getId());

            scheduleHeartbeat(session);

        } catch (Exception e) {
            log.error("[GPS WS] Error in connection", e);
            session.close(CloseStatus.SERVER_ERROR.withReason("Server error: " + e.getMessage()));
        }
    }

    /**
     * Intelligently discover which participant this user should be
     */
    private ParticipantDiscoveryResult discoverParticipantForUser(String userId, String businessKey) {
        try {
            // 1. Find all active process instances with this business key
            List<ProcessInstance> instances = runtimeService.createProcessInstanceQuery()
                    .processInstanceBusinessKey(businessKey)
                    .active()
                    .list();

            if (instances.isEmpty()) {
                log.debug("[GPS WS] No active process instances for businessKey: {}", businessKey);
                return null;
            }

            // 2. Collect all possible participants and their states
            Map<String, ParticipantInfo> participantStates = new HashMap<>();

            for (ProcessInstance pi : instances) {
                // Get the process definition to understand participants
                BpmnModelInstance model = repositoryService.getBpmnModelInstance(pi.getProcessDefinitionId());
                Collection<Participant> participants = model.getModelElementsByType(Participant.class);

                for (Participant p : participants) {
                    if (p.getProcess() != null) {
                        String pId = p.getId();
                        String pName = p.getName() != null ? p.getName() : pId;

                        ParticipantInfo info = participantStates.computeIfAbsent(pId,
                                k -> new ParticipantInfo(pId, pName));

                        // Check if this participant has active tasks
                        List<Task> activeTasks = findTasksForParticipant(pi.getId(), pId);
                        info.activeTasks.addAll(activeTasks);

                        // Check if this participant is already claimed by another user
                        String existingUser = userParticipantMapping.getUserForParticipant(businessKey, pId);
                        if (existingUser != null) {
                            info.claimedByUser = existingUser;
                        }
                    }
                }
            }

            // 3. Strategy: Find the best participant for this user

            // 3a. First priority: Tasks already assigned to this user
            for (ParticipantInfo info : participantStates.values()) {
                for (Task task : info.activeTasks) {
                    if (userId.equals(task.getAssignee())) {
                        log.info("[GPS WS] Found participant {} with task assigned to user {}",
                                info.participantId, userId);
                        return new ParticipantDiscoveryResult(info.participantId, info.participantName);
                    }
                }
            }

            // 3b. Second priority: Unclaimed participant with tasks this user can access
            for (ParticipantInfo info : participantStates.values()) {
                if (info.claimedByUser == null) { // Not claimed by another user
                    for (Task task : info.activeTasks) {
                        if (taskTrackingService.canUserAccessTask(userId, task)) {
                            log.info("[GPS WS] Found unclaimed participant {} with accessible tasks for user {}",
                                    info.participantId, userId);
                            return new ParticipantDiscoveryResult(info.participantId, info.participantName);
                        }
                    }
                }
            }

            // 3c. Third priority: Based on user groups/roles matching participant names
            List<Group> userGroups = identityService.createGroupQuery()
                    .groupMember(userId)
                    .list();

            for (ParticipantInfo info : participantStates.values()) {
                if (info.claimedByUser == null) {
                    // Match based on group names vs participant names
                    for (Group group : userGroups) {
                        if (info.participantName.toLowerCase().contains(group.getId().toLowerCase()) ||
                                group.getId().toLowerCase().contains(info.participantName.toLowerCase())) {

                            log.info("[GPS WS] Matched participant {} to user {} based on group {}",
                                    info.participantId, userId, group.getId());
                            return new ParticipantDiscoveryResult(info.participantId, info.participantName);
                        }
                    }
                }
            }

            // 3d. Last resort: First available unclaimed participant
            for (ParticipantInfo info : participantStates.values()) {
                if (info.claimedByUser == null && !info.activeTasks.isEmpty()) {
                    log.info("[GPS WS] Assigning first available participant {} to user {}",
                            info.participantId, userId);
                    return new ParticipantDiscoveryResult(info.participantId, info.participantName);
                }
            }

            log.debug("[GPS WS] Could not determine specific participant for user {} in BK {}",
                    userId, businessKey);
            return null;

        } catch (Exception e) {
            log.error("[GPS WS] Error during participant discovery", e);
            return null;
        }
    }

    /**
     * Find tasks for a specific participant in a process instance
     */
    private List<Task> findTasksForParticipant(String processInstanceId, String participantId) {
        List<Task> result = new ArrayList<>();

        try {
            // Get all active tasks for this process instance
            List<Task> allTasks = taskService.createTaskQuery()
                    .processInstanceId(processInstanceId)
                    .active()
                    .list();

            for (Task task : allTasks) {
                String taskParticipantId = participantService.resolveParticipantForTask(task);
                if (participantId.equals(taskParticipantId)) {
                    result.add(task);
                }
            }
        } catch (Exception e) {
            log.error("[GPS WS] Error finding tasks for participant", e);
        }

        return result;
    }

    /**
     * Helper classes for participant discovery
     */
    private static class ParticipantInfo {
        String participantId;
        String participantName;
        List<Task> activeTasks = new ArrayList<>();
        String claimedByUser = null;

        ParticipantInfo(String participantId, String participantName) {
            this.participantId = participantId;
            this.participantName = participantName;
        }
    }

    private static class ParticipantDiscoveryResult {
        String participantId;
        String participantName;

        ParticipantDiscoveryResult(String participantId, String participantName) {
            this.participantId = participantId;
            this.participantName = participantName;
        }
    }





    private void handleLocationUpdate(WebSocketSession session, String userId, String businessKey,
                                      GpsMessage.LocationUpdate location) throws IOException {

        if (location.getLat() == null || location.getLon() == null) {
            sendError(session, "INVALID_LOCATION", "Missing latitude or longitude");
            return;
        }

        if (Math.abs(location.getLat()) > 90 || Math.abs(location.getLon()) > 180) {
            sendError(session, "INVALID_COORDINATES", "Invalid GPS coordinates");
            return;
        }

        log.info("[GPS WS] Location update - User: {}, BK: {}, Location: ({}, {})",
                userId, businessKey, location.getLat(), location.getLon());

        try {

            if (location.getBusinessKey() != null && !location.getBusinessKey().isBlank()) {
                businessKey = location.getBusinessKey();
            }

            // Lazy discovery if needed
            String existingParticipantId = userParticipantMapping.getParticipantIdForUser(businessKey, userId);

            if (existingParticipantId == null && businessKey != null) {
                log.info("[GPS WS] Attempting lazy discovery for user {} in BK {}", userId, businessKey);

                UserParticipantMappingService.ParticipantDiscoveryResult discovery =
                        userParticipantMapping.autoDiscoverAndRegister(userId, businessKey);

                if (discovery != null) {
                    log.info("[GPS WS] Lazy discovery successful: user {} is now participant {}",
                            userId, discovery.getParticipantId());

                    // Notify client
                    Map<String, Object> assignmentData = Map.of(
                            "participantId", discovery.getParticipantId(),
                            "participantName", discovery.getParticipantName(),
                            "businessKey", businessKey
                    );

                    sendMessage(session, GpsResponse.success("PARTICIPANT_ASSIGNED",
                            "Assigned as " + discovery.getParticipantName(), assignmentData));
                }
            }
            // Process location for ALL active tasks with this businessKey
            Map<String, Object> result = processLocationForBusinessKey(
                    userId,
                    businessKey,
                    location.getLat(),
                    location.getLon()
            );

            // Send response
            GpsResponse wsResponse = GpsResponse.success("LOCATION_PROCESSED",
                    "Location update processed", result);

            sendMessage(session, wsResponse);

        } catch (Exception e) {
            log.error("[GPS WS] Failed to process location: {}", e.getMessage(), e);
            sendError(session, "PROCESSING_FAILED", "Failed to process location update");
        }
    }

    private Map<String, Object> processLocationForBusinessKey(String userId, String businessKey,
                                                              double lat, double lon) {
        Map<String, Object> result = new HashMap<>();

        if (businessKey == null || businessKey.isBlank()) {
            log.warn("[GPS WS] No businessKey provided");
            result.put("error", "No businessKey provided");
            return result;
        }

        // ALWAYS use the mapped participantId, not userId
        String participantId = userParticipantMapping.getParticipantIdForUser(businessKey, userId);

        if (participantId == null) {
            log.warn("[GPS WS] No participant mapping for user {} in BK {}. " +
                    "Cannot process location properly.", userId, businessKey);
            // Use userId as fallback but warn
            participantId = userId;
            result.put("warning", "No participant mapping - using userId as fallback");
        }

        log.debug("[GPS WS] Processing location for user {} as participant {} in BK {}",
                userId, participantId, businessKey);

        // Update position using the correct participantId
        String currentPlace = updatePosition(participantId, lat, lon);
        result.put("currentPlace", currentPlace);
        result.put("participantId", participantId);

        // Process ALL types of tasks for this businessKey
        List<String> triggeredEvents = new ArrayList<>();

        // 1. Check movement tasks
        boolean movementCompleted = checkAndSignalMovementTasks(businessKey, userId, lat, lon);
        if (movementCompleted) {
            triggeredEvents.add("MOVEMENT_COMPLETED");
        }

        // 2. Check binding readiness
        boolean bindingReady = checkAndSignalBindings(businessKey, userId);
        if (bindingReady) {
            triggeredEvents.add("BINDING_READY");
        }

        // 3. Check unbinding readiness
        boolean unbindingReady = checkAndSignalUnbindings(businessKey, userId);
        if (unbindingReady) {
            triggeredEvents.add("UNBINDING_READY");
        }

        result.put("triggeredEvents", triggeredEvents);
        result.put("userId", userId);
        result.put("businessKey", businessKey);
        result.put("location", Map.of("lat", lat, "lon", lon));

        log.info("[GPS WS] Processed location for BK {}: triggered {}", businessKey, triggeredEvents);

        return result;
    }

    private String updatePosition(String participantId, double lat, double lon) {
        Optional<PhysicalPlace> place = environmentDataService.findPhysicalPlaceContainingLocation(lat, lon);
        String placeId = place.map(PhysicalPlace::getId).orElse(null);

        // Aggiorna posizione per il participant
        positionService.updatePosition(participantId, lat, lon, placeId);

        log.trace("[GPS WS] Updated position for participant {} in place: {}",
                participantId, placeId);

        return placeId;
    }


    private boolean checkAndSignalMovementTasks(String businessKey, String userId, double lat, double lon) {
        // Find all active movement tasks for this businessKey
        List<ProcessInstance> instances = runtimeService.createProcessInstanceQuery()
                .processInstanceBusinessKey(businessKey)
                .active()
                .list();

        for (ProcessInstance pi : instances) {
            List<Execution> executions = runtimeService.createExecutionQuery()
                    .processInstanceId(pi.getProcessInstanceId())
                    .active()
                    .list();

            for (Execution exe : executions) {
                List<String> activityIds = runtimeService.getActiveActivityIds(exe.getId());

                for (String activityId : activityIds) {
                    // Check if this is a movement task with a destination
                    String destinationKey = activityId + ".destination";
                    Object destination = runtimeService.getVariable(exe.getId(), destinationKey);

                    if (destination != null) {
                        String destinationRef = destination.toString();
                        String resolvedPhysicalDestination = environmentDataService.resolvePhysicalPlaceId(destinationRef)
                            .orElse(null);
                        String resolvedLogicalDestination = environmentDataService.resolveLogicalPlaceId(destinationRef)
                            .orElse(null);

                        boolean reachedDestination = false;
                        String destinationForLog = destinationRef;

                        if (resolvedPhysicalDestination != null) {
                            reachedDestination = environmentDataService
                                .isLocationInPhysicalPlace(lat, lon, resolvedPhysicalDestination);
                            destinationForLog = resolvedPhysicalDestination;
                        } else if (resolvedLogicalDestination != null) {
                            reachedDestination = movementTaskRegistry
                                .resolveMatchingPhysicalPlaceIdsForLogicalDestination(resolvedLogicalDestination)
                                .stream()
                                .anyMatch(placeId -> environmentDataService.isLocationInPhysicalPlace(lat, lon, placeId));
                            destinationForLog = resolvedLogicalDestination;
                        }

                        if (reachedDestination) {
                            log.info("[GPS WS] MOVEMENT COMPLETED - User {} reached {} for task {}",
                                userId, destinationForLog, activityId);

                            // Signal the execution
                            runtimeService.signal(exe.getId());
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    private boolean checkAndSignalBindings(String businessKey, String userId) {
        // Get the correct participant ID for this user
        String participantId = userParticipantMapping.getParticipantIdForUser(businessKey, userId);

        if (participantId == null) {
            log.warn("[GPS WS] No participant mapping for user {} in BK {}, cannot check bindings",
                    userId, businessKey);
            return false;
        }

        List<WaitingBinding> waitingBindings = bindingService.getAllWaitingBindings();

        for (WaitingBinding wb : waitingBindings) {
            if (!wb.getBusinessKey().equals(businessKey)) continue;

            // Check if this participant is involved
            boolean isCurrentParticipant = wb.getCurrentParticipantId().equals(participantId);
            boolean isTargetParticipant = wb.getTargetParticipantId().equals(participantId);

            if (!isCurrentParticipant && !isTargetParticipant) {
                continue;
            }

            // Determina chi è l'altro partecipante
            String otherParticipantId = isCurrentParticipant ?
                    wb.getTargetParticipantId() : wb.getCurrentParticipantId();

            // Cerca il WaitingBinding dell'altro partecipante
            Optional<WaitingBinding> otherWaiting = bindingService.findWaitingBinding(
                    businessKey, otherParticipantId);

            if (!otherWaiting.isPresent()) {
                log.debug("[GPS WS] Other participant {} not waiting yet for binding", otherParticipantId);
                continue;
            }

            // Ora controlla se sono nella stessa place
                PhysicalPlace bindingPlace = proximityService.getBindingPlace(
                    wb.getCurrentParticipantId(),
                    wb.getTargetParticipantId());

            if (bindingPlace != null) {
                log.info("[GPS WS] BINDING READY - Participants {} and {} in same place: {}",
                        wb.getCurrentParticipantId(),
                        wb.getTargetParticipantId(),
                        bindingPlace.getName());

                // IMPORTANTE: Prendi gli executionId PRIMA di rimuovere i binding!
                String execution1 = wb.getExecutionId();
                String execution2 = otherWaiting.get().getExecutionId();

                // Rimuovi i waiting bindings
                bindingService.removeWaitingBinding(businessKey, wb.getCurrentParticipantId());
                bindingService.removeWaitingBinding(businessKey, wb.getTargetParticipantId());

                // Segnala ENTRAMBI gli execution
                try {
                    log.info("[GPS WS] Signaling execution {} for participant {}",
                            execution1, wb.getCurrentParticipantId());
                    runtimeService.signal(execution1);
                } catch (Exception e) {
                    log.error("[GPS WS] Failed to signal execution {}: {}", execution1, e.getMessage());
                }

                try {
                    log.info("[GPS WS] Signaling execution {} for participant {}",
                            execution2, otherWaiting.get().getCurrentParticipantId());
                    runtimeService.signal(execution2);
                } catch (Exception e) {
                    log.error("[GPS WS] Failed to signal execution {}: {}", execution2, e.getMessage());
                }

                return true;
            }
        }

        return false;
    }

    private boolean checkAndSignalUnbindings(String businessKey, String userId) {
        String participantId = userParticipantMapping.getParticipantIdForUser(businessKey, userId);

        if (participantId == null) {
            log.warn("[GPS WS] No participant mapping for user {} in BK {}, cannot check unbindings",
                    userId, businessKey);
            return false;
        }

        List<WaitingBinding> waitingUnbindings = bindingService.getAllWaitingUnbindings();

        for (WaitingBinding wu : waitingUnbindings) {
            if (!wu.getBusinessKey().equals(businessKey)) continue;

            // Check if this participant is involved
            boolean isCurrentParticipant = wu.getCurrentParticipantId().equals(participantId);
            boolean isTargetParticipant = wu.getTargetParticipantId().equals(participantId);

            if (!isCurrentParticipant && !isTargetParticipant) {
                continue;
            }

            // Find the other participant
            String otherParticipantId = isCurrentParticipant ?
                    wu.getTargetParticipantId() : wu.getCurrentParticipantId();

            // Check if both are waiting for unbinding
            Optional<WaitingBinding> otherWaiting = bindingService.findWaitingUnbinding(
                    businessKey, otherParticipantId);

            if (!otherWaiting.isPresent()) {
                log.debug("[GPS WS] Other participant {} not waiting yet for unbinding", otherParticipantId);
                continue;
            }

            // CRITICAL FIX: Use CURRENT positions to check proximity
                PhysicalPlace unbindingPlace = proximityService.getBindingPlace(
                    wu.getCurrentParticipantId(),
                    wu.getTargetParticipantId());

            if (unbindingPlace != null) {
                log.info("[GPS WS] UNBINDING READY - Participants {} and {} in same place: {}",
                        wu.getCurrentParticipantId(),
                        wu.getTargetParticipantId(),
                        unbindingPlace.getName());

                String execution1 = wu.getExecutionId();
                String execution2 = otherWaiting.get().getExecutionId();

                if (!isExecutionActive(execution1)) {
                    log.error("[GPS WS] Execution {} for participant {} is no longer active!",
                            execution1, wu.getCurrentParticipantId());
                    bindingService.removeWaitingUnbinding(businessKey, wu.getCurrentParticipantId());
                    continue;
                }

                if (!isExecutionActive(execution2)) {
                    log.error("[GPS WS] Execution {} for participant {} is no longer active!",
                            execution2, otherWaiting.get().getCurrentParticipantId());
                    bindingService.removeWaitingUnbinding(businessKey, otherParticipantId);
                    continue;
                }

                // Remove BOTH unbindings using correct keys
                bindingService.removeWaitingUnbinding(businessKey, wu.getCurrentParticipantId());
                bindingService.removeWaitingUnbinding(businessKey, otherParticipantId);

                // Signal both executions
                try {
                    log.info("[GPS WS] Signaling unbinding execution {} for participant {}",
                            execution1, wu.getCurrentParticipantId());
                    runtimeService.signal(execution1);
                } catch (Exception e) {
                    log.error("[GPS WS] Failed to signal unbinding execution {}: {}",
                            execution1, e.getMessage());
                }

                try {
                    log.info("[GPS WS] Signaling unbinding execution {} for participant {}",
                            execution2, otherParticipantId);
                    runtimeService.signal(execution2);
                } catch (Exception e) {
                    log.error("[GPS WS] Failed to signal unbinding execution {}: {}",
                            execution2, e.getMessage());
                }

                return true;
            } else {
                // Debug why they're not in same place
                ProximityService.BindingReadiness readiness = proximityService.checkBindingReadiness(
                        wu.getCurrentParticipantId(), wu.getTargetParticipantId());
                log.debug("[GPS WS] Unbinding not ready: {}", readiness.message());
            }
        }

        return false;
    }

    // Metodo helper per verificare se un execution è ancora attivo
    private boolean isExecutionActive(String executionId) {
        try {
            Execution execution = runtimeService.createExecutionQuery()
                    .executionId(executionId)
                    .singleResult();
            return execution != null;
        } catch (Exception e) {
            log.error("[GPS WS] Error checking execution {}: {}", executionId, e.getMessage());
            return false;
        }
    }

    private String getBusinessKey(WebSocketSession session) {
        return (String) session.getAttributes().get("businessKey");
    }

    private void handleHeartbeat(WebSocketSession session, String userId) throws IOException {
        log.trace("[GPS WS] Heartbeat from userId: {}", userId);
        sendMessage(session, GpsResponse.ack("HEARTBEAT"));
    }

    private void handleStartTracking(WebSocketSession session, String userId, GpsMessage.StartTracking start)
            throws IOException {
        log.info("[GPS WS] Start tracking for userId: {}, businessKey: {}",
                userId, start.getBusinessKey());

        // Store tracking preferences
        if (start.getBusinessKey() != null) {
            sessionManager.setTrackingProcess(userId, start.getBusinessKey());
        }

        GpsResponse response = GpsResponse.success("TRACKING_STARTED",
                "Started tracking for business key: " + start.getBusinessKey(),
                Map.of("updateInterval", start.getUpdateInterval() != null ? start.getUpdateInterval() : 5));

        sendMessage(session, response);
    }

    private void handleStopTracking(WebSocketSession session, String userId, GpsMessage.StopTracking stop)
            throws IOException {
        log.info("[GPS WS] Stop tracking for userId: {}, businessKey: {}",
                userId, stop.getBusinessKey());

        sessionManager.clearTrackingProcess(userId);

        sendMessage(session, GpsResponse.success("TRACKING_STOPPED",
                "Stopped tracking for business key: " + stop.getBusinessKey(),
                null));
    }

    private String getUserId(WebSocketSession session) {
        return (String) session.getAttributes().get("userId");
    }

    private void sendMessage(WebSocketSession session, GpsResponse response) throws IOException {
        if (session.isOpen()) {
            String json = objectMapper.writeValueAsString(response);
            session.sendMessage(new TextMessage(json));
        }
    }

    private void sendError(WebSocketSession session, String errorType, String message) throws IOException {
        sendMessage(session, GpsResponse.error(errorType, message));
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String userId = getUserId(session);
        log.error("[GPS WS] Transport error for userId: {}", userId, exception);
        sessionManager.removeSession(userId, session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String userId = getUserId(session);
        sessionManager.removeSession(userId, session.getId());
        lastActivity.remove(session.getId());
        log.info("[GPS WS] Connection closed - userId: {}, status: {}", userId, status);
    }

    private void scheduleHeartbeat(WebSocketSession session) {
        heartbeatExecutor.scheduleWithFixedDelay(() -> {
            try {
                if (!session.isOpen()) {
                    return;
                }

                Long lastActivityTime = lastActivity.get(session.getId());
                if (lastActivityTime != null) {
                    long inactiveTime = System.currentTimeMillis() - lastActivityTime;

                    if (inactiveTime > 60000) {
                        log.warn("[GPS WS] Closing inactive session: {}", session.getId());
                        session.close(CloseStatus.GOING_AWAY.withReason("Inactive"));
                    } else if (inactiveTime > 30000) {
                        session.sendMessage(new PingMessage());
                    }
                }
            } catch (Exception e) {
                log.error("[GPS WS] Heartbeat error for session: {}", session.getId(), e);
            }
        }, 30, 30, TimeUnit.SECONDS);
    }
}