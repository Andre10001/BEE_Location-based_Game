// src/main/java/org/unicam/intermediate/service/environmental/movement/GpsProcessingService.java

package org.unicam.intermediate.service.environmental.movement;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.engine.runtime.Execution;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Service;
import org.unicam.intermediate.models.WaitingBinding;
import org.unicam.intermediate.models.pojo.PhysicalPlace;
import org.unicam.intermediate.models.record.MovementResponse;
import org.unicam.intermediate.models.record.MovementTask;
import org.unicam.intermediate.models.enums.TaskType;
import org.unicam.intermediate.service.environmental.BindingService;
import org.unicam.intermediate.service.environmental.EnvironmentDataService;
import org.unicam.intermediate.service.environmental.ProximityService;
import org.unicam.intermediate.service.participant.ParticipantPositionService;
import org.unicam.intermediate.service.participant.UserParticipantMappingService;
import org.unicam.intermediate.service.xml.AbstractXmlService;
import org.unicam.intermediate.service.xml.XmlServiceDispatcher;
import org.unicam.intermediate.utils.Constants;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@Slf4j
@AllArgsConstructor
public class GpsProcessingService {

    private final RuntimeService runtimeService;
    private final RepositoryService repositoryService;
    private final MovementService movementService;
    private final BindingService bindingService;
    private final ProximityService proximityService;
    private final EnvironmentDataService environmentDataService;
    private final ParticipantPositionService positionService;
    private final UserParticipantMappingService userParticipantMapping;
    private final XmlServiceDispatcher xmlServiceDispatcher;

    private static final ExecutorService executorService = Executors.newFixedThreadPool(5);

    /**
     * Main entry point for processing GPS coordinates from a user
     * Handles ALL task types: movement, binding, unbinding
     */
    public MovementResponse processUserLocation(String userId, double lat, double lon) {
        log.debug("[GPS Service] Processing location for user: {} at ({}, {})", userId, lat, lon);

        // Get participant context for this user
        UserParticipantMappingService.TrackingContext context =
                userParticipantMapping.getActiveTracking(userId);

        if (context == null) {
            log.debug("[GPS Service] No active tracking context for user: {}", userId);
            // Still update position but with userId as participantId
            updateParticipantPosition(userId, lat, lon);
            return MovementResponse.noActiveTasks(userId);
        }

        String participantId = context.getParticipantId();
        String businessKey = context.getBusinessKey();

        // Always update position first
        updateParticipantPosition(participantId, lat, lon);

        // 1. Check MOVEMENT tasks
        List<MovementTask> movementTasks = findActiveMovementTasksForUser(userId);
        for (MovementTask task : movementTasks) {
            if (isLocationMatchingDestination(lat, lon, task.destinationId())) {
                handleMovementCompletion(task, userId, participantId, lat, lon);
                return MovementResponse.enteredArea(userId, task.destinationId(), task.processInstanceId());
            }
        }

        // 2. Check BINDING readiness
        if (businessKey != null) {
            boolean bindingTriggered = checkAndTriggerBindings(participantId, businessKey);
            if (bindingTriggered) {
                log.info("[GPS Service] Binding conditions met for participant: {}", participantId);
                // Don't return success here, as binding is a background process
            }

            // 3. Check UNBINDING readiness
            boolean unbindingTriggered = checkAndTriggerUnbindings(participantId, businessKey);
            if (unbindingTriggered) {
                log.info("[GPS Service] Unbinding conditions met for participant: {}", participantId);
                // Don't return success here, as unbinding is a background process
            }
        }

        // No movement task completed
        if (!movementTasks.isEmpty()) {
            log.info("[GPS Service] User {} not in any target area. Active destinations: {}",
                    userId, movementTasks.stream().map(MovementTask::destinationId).toList());
            return MovementResponse.notInTargetArea(userId);
        }

        return MovementResponse.noActiveTasks(userId);
    }

    /**
     * Check if waiting bindings can now proceed
     */
    private boolean checkAndTriggerBindings(String participantId, String businessKey) {
        List<WaitingBinding> waitingBindings = bindingService.getAllWaitingBindings();
        boolean triggered = false;

        for (WaitingBinding wb : waitingBindings) {
            if (!wb.getBusinessKey().equals(businessKey)) continue;

            // Check if this participant is involved
            if (wb.getCurrentParticipantId().equals(participantId) ||
                    wb.getTargetParticipantId().equals(participantId)) {

                // Check if both participants are in the same place
                PhysicalPlace bindingPlace = proximityService.getBindingPlace(
                        wb.getCurrentParticipantId(),
                        wb.getTargetParticipantId());

                if (bindingPlace != null) {
                    log.info("[GPS Service] Binding ready! Participants {} and {} are both in {}",
                            wb.getCurrentParticipantId(), wb.getTargetParticipantId(),
                            bindingPlace.getName());

                    // Find the matching waiting binding for the other participant
                    Optional<WaitingBinding> otherWaiting = bindingService.findWaitingBinding(
                            businessKey, wb.getTargetParticipantId());

                    if (otherWaiting.isPresent()) {
                        // Both are waiting and in same place - signal both
                        signalBinding(wb, otherWaiting.get());
                        triggered = true;
                    }
                }
            }
        }

        return triggered;
    }

    /**
     * Check if waiting unbindings can now proceed
     */
    private boolean checkAndTriggerUnbindings(String participantId, String businessKey) {
        List<WaitingBinding> waitingUnbindings = bindingService.getAllWaitingUnbindings();
        boolean triggered = false;

        for (WaitingBinding wu : waitingUnbindings) {
            if (!wu.getBusinessKey().equals(businessKey)) continue;

            // Check if this participant is involved
            if (wu.getCurrentParticipantId().equals(participantId) ||
                    wu.getTargetParticipantId().equals(participantId)) {

                // Check if both participants are in the same place
                PhysicalPlace unbindingPlace = proximityService.getBindingPlace(
                        wu.getCurrentParticipantId(),
                        wu.getTargetParticipantId());

                if (unbindingPlace != null) {
                    log.info("[GPS Service] Unbinding ready! Participants {} and {} are both in {}",
                            wu.getCurrentParticipantId(), wu.getTargetParticipantId(),
                            unbindingPlace.getName());

                    // Find the matching waiting unbinding for the other participant
                    Optional<WaitingBinding> otherWaiting = bindingService.findWaitingUnbinding(
                            businessKey, wu.getTargetParticipantId());

                    if (otherWaiting.isPresent()) {
                        // Both are waiting and in same place - signal both
                        signalUnbinding(wu, otherWaiting.get());
                        triggered = true;
                    }
                }
            }
        }

        return triggered;
    }

    private void signalBinding(WaitingBinding binding1, WaitingBinding binding2) {
        CompletableFuture.runAsync(() -> {
            try {
                // Remove from waiting
                bindingService.removeWaitingBinding(binding1.getBusinessKey(), binding1.getTargetParticipantId());
                bindingService.removeWaitingBinding(binding2.getBusinessKey(), binding2.getTargetParticipantId());

                // Signal both executions
                runtimeService.signal(binding1.getExecutionId());
                runtimeService.signal(binding2.getExecutionId());

                log.info("[GPS Service] Successfully signaled binding for participants {} and {}",
                        binding1.getCurrentParticipantId(), binding2.getCurrentParticipantId());

            } catch (Exception e) {
                log.error("[GPS Service] Failed to signal binding", e);
            }
        }, executorService);
    }

    private void signalUnbinding(WaitingBinding unbinding1, WaitingBinding unbinding2) {
        CompletableFuture.runAsync(() -> {
            try {
                // Remove from waiting
                bindingService.removeWaitingUnbinding(unbinding1.getBusinessKey(), unbinding1.getTargetParticipantId());
                bindingService.removeWaitingUnbinding(unbinding2.getBusinessKey(), unbinding2.getTargetParticipantId());

                // Signal both executions
                runtimeService.signal(unbinding1.getExecutionId());
                runtimeService.signal(unbinding2.getExecutionId());

                log.info("[GPS Service] Successfully signaled unbinding for participants {} and {}",
                        unbinding1.getCurrentParticipantId(), unbinding2.getCurrentParticipantId());

            } catch (Exception e) {
                log.error("[GPS Service] Failed to signal unbinding", e);
            }
        }, executorService);
    }

    /**
     * Process location for a specific process instance
     */
    public MovementResponse processLocationForProcess(String userId, double lat, double lon, String processInstanceId) {
        log.debug("[GPS Service] Processing location for user: {} in process: {}", userId, processInstanceId);

        // Get participant context
        UserParticipantMappingService.TrackingContext context =
                userParticipantMapping.getActiveTracking(userId);
        String participantId = context != null ? context.getParticipantId() : userId;

        updateParticipantPosition(participantId, lat, lon);

        List<MovementTask> tasks = findMovementTasksForProcess(processInstanceId, userId);

        for (MovementTask task : tasks) {
            if (isLocationMatchingDestination(lat, lon, task.destinationId())) {
                handleMovementCompletion(task, userId, participantId, lat, lon);
                return MovementResponse.enteredArea(userId, task.destinationId(), task.processInstanceId());
            }
        }

        return MovementResponse.notInTargetArea(userId);
    }

    private void updateParticipantPosition(String participantId, double lat, double lon) {
        // Find which place (if any) contains this location
        String currentPlace = environmentDataService.findPhysicalPlaceContainingLocation(lat, lon)
            .map(PhysicalPlace::getId)
                .orElse(null);

        positionService.updatePosition(participantId, lat, lon, currentPlace);
        log.trace("[GPS Service] Updated position for participant {} to ({}, {}) in place: {}",
                participantId, lat, lon, currentPlace);
    }

    private List<MovementTask> findActiveMovementTasksForUser(String userId) {
        List<MovementTask> tasks = new ArrayList<>();

        // Get all active process definitions with movement tasks
        var definitions = movementService.getActiveProcessDefinitionsWithMovementTasks();

        for (var def : definitions) {
            List<String> movementTaskIds = movementService.getTasksOfType(def, TaskType.MOVEMENT);
            if (movementTaskIds.isEmpty()) continue;

            // Find executions for these tasks that this user can access
            List<Execution> executions = movementService
                    .findActiveExecutionsForActivities(def.getId(), movementTaskIds, userId);

            for (Execution exe : executions) {
                MovementTask task = extractMovementTask(exe);
                if (task != null) {
                    tasks.add(task);
                }
            }
        }

        log.debug("[GPS Service] Found {} active movement tasks for user {}", tasks.size(), userId);
        return tasks;
    }

    private List<MovementTask> findMovementTasksForProcess(String processInstanceId, String userId) {
        List<MovementTask> tasks = new ArrayList<>();

        // Get process instance
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        if (processInstance == null) {
            return tasks;
        }

        // Get process definition
        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(processInstance.getProcessDefinitionId())
                .singleResult();

        if (definition == null) {
            return tasks;
        }

        // Use the MovementService to get movement task IDs
        List<String> movementTaskIds = movementService.getTasksOfType(definition, TaskType.MOVEMENT);

        if (movementTaskIds.isEmpty()) {
            return tasks;
        }

        // Find executions for these tasks
        List<Execution> executions = movementService.findActiveExecutionsForActivities(
                definition.getId(),
                movementTaskIds,
                userId
        );

        // Extract MovementTask objects
        for (Execution exe : executions) {
            MovementTask task = extractMovementTask(exe);
            if (task != null) {
                tasks.add(task);
            }
        }

        return tasks;
    }

    private MovementTask extractMovementTask(Execution execution) {
        List<String> activeIds = runtimeService.getActiveActivityIds(execution.getId());
        if (activeIds.isEmpty()) {
            return null;
        }

        String taskId = activeIds.get(0);

        // Get destination from process variables
        AbstractXmlService xmlSvc = xmlServiceDispatcher
                .get(Constants.SPACE_NS.getNamespaceUri(), TaskType.MOVEMENT);

        if (xmlSvc == null) {
            log.warn("[GPS Service] No XML service found for movement tasks");
            return null;
        }

        String varKey = taskId + "." + xmlSvc.getLocalName();
        String destinationId = (String) runtimeService.getVariable(execution.getId(), varKey);

        if (destinationId == null) {
            log.trace("[GPS Service] No destination found for task {} in execution {}",
                    taskId, execution.getId());
            return null;
        }

        return new MovementTask(
                execution.getId(),
                execution.getProcessInstanceId(),
                taskId,
                destinationId,
                execution
        );
    }

    private boolean isLocationMatchingDestination(double lat, double lon, String destinationId) {
        boolean matches = environmentDataService.isLocationInPhysicalPlace(lat, lon, destinationId);

        if (matches) {
            log.debug("[GPS Service] Location ({}, {}) matches destination: {}",
                    lat, lon, destinationId);
        }

        return matches;
    }

    private void handleMovementCompletion(MovementTask task, String userId,
                                          String participantId, double lat, double lon) {
        log.info("[GPS Service] MATCH! User: {} (Participant: {}) entered area: {} | Task: {} | Process: {}",
                userId, participantId, task.destinationId(), task.taskId(), task.processInstanceId());

        // Update position with the confirmed destination
        positionService.updatePosition(participantId, lat, lon, task.destinationId());

        // Signal the execution to continue
        signalTaskCompletion(task);
    }

    private void signalTaskCompletion(MovementTask task) {
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(50);
                runtimeService.signal(task.executionId());
                log.info("[GPS Service] Successfully signaled task {} completion", task.taskId());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("[GPS Service] Interrupted while signaling execution {}", task.executionId());
            } catch (Exception e) {
                log.error("[GPS Service] Failed to signal execution {} for task {}: {}",
                        task.executionId(), task.taskId(), e.getMessage(), e);
            }
        }, executorService);
    }

    public boolean hasActiveMovementTasks(String userId) {
        return !findActiveMovementTasksForUser(userId).isEmpty();
    }

    public List<String> getActiveDestinations(String userId) {
        return findActiveMovementTasksForUser(userId).stream()
                .map(MovementTask::destinationId)
                .distinct()
                .toList();
    }
}