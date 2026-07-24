package org.unicam.intermediate.service.environmental.movement;

import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.RuntimeService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.unicam.intermediate.models.pojo.Condition;
import org.unicam.intermediate.models.pojo.LogicalPlace;
import org.unicam.intermediate.models.pojo.PhysicalPlace;
import org.unicam.intermediate.models.record.MovementTaskInfo;
import org.unicam.intermediate.service.environmental.EnvironmentDataService;
import org.unicam.intermediate.service.participant.ParticipantDataService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class MovementTaskRegistry {

    private static final String BPMN_ERROR_CODE_VAR = "__spaceBpmnErrorCode";
    private static final String BPMN_ERROR_MESSAGE_VAR = "__spaceBpmnErrorMessage";
    private static final String FAILED_MOVEMENT_ERROR_CODE = "failedMovement";
    private static final String PARTICIPANT_DESTINATION_PREFIX = "Participant.";
    private static final String PARTICIPANT_POSITION_SUFFIX = ".position";

    private final EnvironmentDataService environmentDataService;
    private final ParticipantDataService participantDataService;
    private final RuntimeService runtimeService;

    // Registry: participantId -> MovementTaskInfo
    private final Map<String, MovementTaskInfo> activeMovementTasks = new ConcurrentHashMap<>();
    // executionId -> epoch millis when movement timer started
    private final Map<String, Long> movementTimerStartByExecutionId = new ConcurrentHashMap<>();

    public MovementTaskRegistry(EnvironmentDataService environmentDataService,
                                ParticipantDataService participantDataService,
                                RuntimeService runtimeService) {
        this.environmentDataService = environmentDataService;
        this.participantDataService = participantDataService;
        this.runtimeService = runtimeService;
    }

    /**
     * Registra un nuovo movement task per un participant
     */
    public void registerTask(String participantId,
                             String executionId,
                             String activityId,
                             String destination,
                             Double timer) {
        if (participantId == null || executionId == null || activityId == null || destination == null) {
            log.warn("[MovementRegistry] Cannot register task with null values: participantId={}, executionId={}, activityId={}, destination={}",
                    participantId, executionId, activityId, destination);
            return;
        }

        String normalizedDestination = resolveDestinationReference(destination);
        MovementTaskInfo taskInfo = new MovementTaskInfo(executionId, normalizedDestination, participantId, activityId, timer);
        activeMovementTasks.put(participantId, taskInfo);
        movementTimerStartByExecutionId.remove(executionId);
        
        log.info("[MovementRegistry] Registered movement task | Participant: {} | Activity: {} | Destination: {} | ExecutionId: {} | Timer: {}",
                participantId, activityId, normalizedDestination, executionId, timer != null ? timer : "(empty)");
    }

    /**
     * Rimuovi un task dal registro
     */
    public void removeTask(String participantId) {
        MovementTaskInfo removed = activeMovementTasks.remove(participantId);
        if (removed != null) {
            movementTimerStartByExecutionId.remove(removed.executionId());
            log.info("[MovementRegistry] Removed movement task for participant: {}", participantId);
        }
    }

    /**
     * Remove task only if the current task for participant matches the expected execution id.
     */
    public boolean removeTask(String participantId, String expectedExecutionId) {
        if (participantId == null || expectedExecutionId == null) {
            return false;
        }

        MovementTaskInfo current = activeMovementTasks.get(participantId);
        if (current == null) {
            return false;
        }

        if (!Objects.equals(current.executionId(), expectedExecutionId)) {
            log.debug("[MovementRegistry] Skip remove for participant {}: expected execution {}, found {}",
                    participantId, expectedExecutionId, current.executionId());
            return false;
        }

        boolean removed = activeMovementTasks.remove(participantId, current);
        if (removed) {
            movementTimerStartByExecutionId.remove(expectedExecutionId);
            log.info("[MovementRegistry] Removed movement task for participant: {} (execution={})",
                    participantId, expectedExecutionId);
        }
        return removed;
    }

    public boolean hasActiveMovementTask(String participantId) {
        return participantId != null && activeMovementTasks.containsKey(participantId);
    }

    /**
     * Returns pending movement tasks for a participant in a shape compatible
     * with the mobile pending-actions view.
     */
    public List<Map<String, String>> getPendingMovementsForParticipant(String participantId) {
        if (participantId == null || participantId.isBlank()) {
            return List.of();
        }

        MovementTaskInfo taskInfo = activeMovementTasks.get(participantId);
        if (taskInfo == null) {
            return List.of();
        }

        String currentPosition = participantDataService.getParticipant(participantId)
                .map(p -> p.getPosition())
                .orElse(null);

        // If already reached, no pending movement notification should be shown.
        if (isDestinationReached(taskInfo.destination(), currentPosition)) {
            return List.of();
        }

        String destinationLabel = resolveDestinationLabel(taskInfo.destination());

        Map<String, String> movementView = new LinkedHashMap<>();
        movementView.put("executionId", taskInfo.executionId());
        movementView.put("action", "move");
        movementView.put("destination", taskInfo.destination());
        movementView.put("message", "Move to " + destinationLabel);

        return List.of(movementView);
    }

    private String resolveDestinationLabel(String destination) {
        if (destination == null || destination.isBlank()) {
            return "destination";
        }

        String resolvedDestination = resolveDestinationReference(destination);

        Optional<PhysicalPlace> physicalPlace = environmentDataService.getPhysicalPlace(resolvedDestination);
        if (physicalPlace.isPresent()) {
            String name = physicalPlace.get().getName();
            return name != null && !name.isBlank() ? name : physicalPlace.get().getId();
        }

        Optional<LogicalPlace> logicalPlace = environmentDataService.getLogicalPlaces().stream()
                .filter(lp -> resolvedDestination.equals(lp.getId()))
                .findFirst();

        if (logicalPlace.isPresent()) {
            String name = logicalPlace.get().getName();
            return name != null && !name.isBlank() ? name : logicalPlace.get().getId();
        }

        return destination;
    }

    /**
     * Resolves the list of physical place IDs matched by a logical destination id.
     * Returns an empty list when destination is not a logical place or when no physical places match.
     */
    public List<String> resolveMatchingPhysicalPlaceIdsForLogicalDestination(String destination) {
        if (destination == null || destination.isBlank()) {
            return List.of();
        }

        String resolvedDestination = environmentDataService.resolveLogicalPlaceId(destination)
                .orElse(destination);

        Optional<LogicalPlace> logicalDestination = environmentDataService.getLogicalPlaces().stream()
                .filter(lp -> resolvedDestination.equals(lp.getId()))
                .findFirst();

        if (logicalDestination.isEmpty()) {
            return List.of();
        }

        return resolvePhysicalPlacesForLogicalPlace(logicalDestination.get()).stream()
                .map(PhysicalPlace::getId)
                .toList();
    }

    /**
     * Controllo periodico: verifica se i participant hanno raggiunto le loro destinazioni
     * Eseguito ogni 2 secondi
     */
    @Scheduled(fixedRate = 2000)
    public void checkMovementCompletion() {
        if (activeMovementTasks.isEmpty()) {
            return;
        }

        log.debug("[MovementRegistry] Checking {} active movement tasks", activeMovementTasks.size());

        List<MovementTaskInfo> completedTasks = new ArrayList<>();

        for (Map.Entry<String, MovementTaskInfo> entry : activeMovementTasks.entrySet()) {
            String participantId = entry.getKey();
            MovementTaskInfo taskInfo = entry.getValue();
            boolean destinationReached = false;

            if (!executionExists(taskInfo.executionId())) {
                log.warn("[MovementRegistry] Execution {} not found for participant {} | removing stale movement task",
                        taskInfo.executionId(), participantId);
                completedTasks.add(taskInfo);
                continue;
            }

            // Ottieni la posizione corrente del participant
            Optional<org.unicam.intermediate.models.pojo.Participant> participantOpt = 
                    participantDataService.getParticipant(participantId);

            if (participantOpt.isPresent()) {
                String currentPosition = participantOpt.get().getPosition();

                // Verifica se ha raggiunto la destinazione
                destinationReached = isDestinationReached(taskInfo.destination(), currentPosition);
                if (destinationReached) {
                    log.info("[MovementRegistry] ✓ Participant {} reached destination {} | Completing task",
                            participantId, taskInfo.destination());
                } else {
                    log.trace("[MovementRegistry] Participant {} at position {}, waiting for {}",
                            participantId, currentPosition, taskInfo.destination());
                }
            } else {
                log.warn("[MovementRegistry] Participant {} not found in environment data", participantId);
            }

            if (destinationReached) {
                if (signalMovement(taskInfo, participantId, false)) {
                    completedTasks.add(taskInfo);
                }
                continue;
            }

            if (isMovementTimerExpired(taskInfo)) {
                runtimeService.setVariableLocal(taskInfo.executionId(), BPMN_ERROR_CODE_VAR, FAILED_MOVEMENT_ERROR_CODE);
                runtimeService.setVariableLocal(
                        taskInfo.executionId(),
                        BPMN_ERROR_MESSAGE_VAR,
                        String.format(
                                "Participant '%s' did not reach destination '%s' within timer for activity '%s'",
                                participantId,
                                taskInfo.destination(),
                                taskInfo.activityId()
                        )
                );

                if (signalMovement(taskInfo, participantId, true)) {
                    completedTasks.add(taskInfo);
                }
            }
        }

        // Rimuovi i task completati dal registro
        completedTasks.forEach(task -> removeTask(task.participantId(), task.executionId()));
    }

    private boolean signalMovement(MovementTaskInfo taskInfo, String participantId, boolean timeout) {
        try {
            runtimeService.signal(taskInfo.executionId());
            if (timeout) {
                log.warn("[MovementRegistry] Movement timer expired -> raised '{}' | participant={} | activity={} | execution={} | destination={} | timer={}s",
                        FAILED_MOVEMENT_ERROR_CODE,
                        participantId,
                        taskInfo.activityId(),
                        taskInfo.executionId(),
                        taskInfo.destination(),
                        taskInfo.timer());
            } else {
                log.info("[MovementRegistry] Task completed successfully for participant {}", participantId);
            }
            return true;
        } catch (Exception e) {
            if (isExecutionNotFoundError(e)) {
                log.warn("[MovementRegistry] Execution {} already ended for participant {}. Cleaning up stale task.",
                        taskInfo.executionId(), participantId);
                return true;
            }
            log.error("[MovementRegistry] Failed to signal execution {} for participant {}: {}",
                    taskInfo.executionId(), participantId, e.getMessage(), e);
            return false;
        }
    }

    private boolean executionExists(String executionId) {
        if (executionId == null || executionId.isBlank()) {
            return false;
        }
        return runtimeService.createExecutionQuery().executionId(executionId).singleResult() != null;
    }

    private boolean isExecutionNotFoundError(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase();
        return normalized.contains("doesn't exist") || normalized.contains("execution is null");
    }

    private boolean isMovementTimerExpired(MovementTaskInfo taskInfo) {
        Double timerSeconds = taskInfo.timer();
        if (timerSeconds == null || timerSeconds <= 0) {
            movementTimerStartByExecutionId.remove(taskInfo.executionId());
            return false;
        }

        long startTime = movementTimerStartByExecutionId.computeIfAbsent(taskInfo.executionId(), ignored -> {
            long now = System.currentTimeMillis();
            log.info("[MovementRegistry] Movement timer started | participant={} | activity={} | execution={} | destination={} | timer={}s",
                    taskInfo.participantId(),
                    taskInfo.activityId(),
                    taskInfo.executionId(),
                    taskInfo.destination(),
                    timerSeconds);
            return now;
        });

        long timeoutMillis = Math.round(timerSeconds * 1000.0d);
        long elapsed = System.currentTimeMillis() - startTime;

        if (elapsed >= timeoutMillis) {
            movementTimerStartByExecutionId.remove(taskInfo.executionId());
            return true;
        }

        return false;
    }

    /**
     * A destination can be:
     * 1) a physical place id (legacy behavior), or
     * 2) a logical place id, in which case completion happens when participant
     *    enters any physical place satisfying all logical-place conditions (AND semantics).
     */
    private boolean isDestinationReached(String destination, String currentPosition) {
        if (destination == null || destination.isBlank() || currentPosition == null || currentPosition.isBlank()) {
            return false;
        }

        String resolvedDestination = resolveDestinationReference(destination);
        String resolvedCurrentPosition = environmentDataService.resolvePhysicalPlaceId(currentPosition)
                .orElse(currentPosition);

        Optional<LogicalPlace> logicalDestination = environmentDataService.getLogicalPlaces().stream()
                .filter(lp -> resolvedDestination.equals(lp.getId()))
                .findFirst();

        // Physical destination: keep current behavior unchanged
        if (logicalDestination.isEmpty()) {
            return resolvedDestination.equals(resolvedCurrentPosition);
        }

        // Logical destination: complete when current physical place is one of the matched places
        List<String> matchingPhysicalPlaces = resolveMatchingPhysicalPlaceIdsForLogicalDestination(resolvedDestination);

        return matchingPhysicalPlaces.contains(resolvedCurrentPosition);
    }

    private String resolveDestinationReference(String destination) {
        if (destination == null || destination.isBlank()) {
            return destination;
        }

        String trimmed = destination.trim();
        if (trimmed.startsWith(PARTICIPANT_DESTINATION_PREFIX)
            && trimmed.length() > PARTICIPANT_DESTINATION_PREFIX.length()) {
            String participantRef = normalizeParticipantReference(
                trimmed.substring(PARTICIPANT_DESTINATION_PREFIX.length()).trim()
            );
            Optional<String> participantPositionOpt = participantDataService.getParticipantByName(participantRef)
                .or(() -> participantDataService.getParticipant(participantRef))
                .map(org.unicam.intermediate.models.pojo.Participant::getPosition)
                .filter(pos -> pos != null && !pos.isBlank());

            if (participantPositionOpt.isPresent()) {
            String participantPosition = participantPositionOpt.get();
            return environmentDataService.resolvePhysicalPlaceId(participantPosition)
                .or(() -> environmentDataService.resolveLogicalPlaceId(participantPosition))
                .orElse(participantPosition);
            }

            return trimmed;
        }

        return environmentDataService.resolvePhysicalPlaceId(trimmed)
            .or(() -> environmentDataService.resolveLogicalPlaceId(trimmed))
            .orElse(trimmed);
    }

    private String normalizeParticipantReference(String participantRef) {
        if (participantRef == null) {
            return null;
        }

        String normalized = participantRef.trim();
        if (normalized.toLowerCase().endsWith(PARTICIPANT_POSITION_SUFFIX)) {
            normalized = normalized.substring(0, normalized.length() - PARTICIPANT_POSITION_SUFFIX.length()).trim();
        }
        return normalized;
    }

    private List<PhysicalPlace> resolvePhysicalPlacesForLogicalPlace(LogicalPlace logicalPlace) {
        List<Condition> conditions = logicalPlace.getConditions();
        if (conditions == null || conditions.isEmpty()) {
            return List.of();
        }

        return environmentDataService.getPhysicalPlaces().stream()
                .filter(physicalPlace -> matchesAllConditions(physicalPlace, conditions))
                .toList();
    }

    private boolean matchesAllConditions(PhysicalPlace physicalPlace, List<Condition> conditions) {
        for (Condition condition : conditions) {
            if (condition == null || condition.getAttribute() == null || condition.getOperator() == null) {
                return false;
            }

            Object actualValue = environmentDataService
                    .getPhysicalPlaceAttribute(physicalPlace.getId(), condition.getAttribute())
                    .orElse(null);
            if (!compare(actualValue, condition.getOperator(), condition.getValue())) {
                return false;
            }
        }

        return true;
    }

    private boolean compare(Object actualValue, String operator, Object expectedValue) {
        if (expectedValue == null) {
            return switch (operator) {
                case "==" -> actualValue == null;
                case "!=" -> actualValue != null;
                default -> false;
            };
        }

        if (actualValue == null) {
            return false;
        }

        String actualText = String.valueOf(actualValue).trim();
        String expectedText = expectedValue == null ? "null" : String.valueOf(expectedValue).trim();

        Double actualNumber = toNumber(actualText);
        Double expectedNumber = toNumber(expectedText);

        if (actualNumber != null && expectedNumber != null) {
            return switch (operator) {
                case "==" -> Double.compare(actualNumber, expectedNumber) == 0;
                case "!=" -> Double.compare(actualNumber, expectedNumber) != 0;
                case ">" -> actualNumber > expectedNumber;
                case "<" -> actualNumber < expectedNumber;
                case ">=" -> actualNumber >= expectedNumber;
                case "<=" -> actualNumber <= expectedNumber;
                default -> false;
            };
        }

        return switch (operator) {
            case "==" -> actualText.equalsIgnoreCase(expectedText);
            case "!=" -> !actualText.equalsIgnoreCase(expectedText);
            default -> false;
        };
    }

    private Double toNumber(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
