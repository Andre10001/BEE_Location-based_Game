package org.unicam.intermediate.service.environmental;

import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.runtime.Execution;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.unicam.intermediate.models.dto.websocket.GpsResponse;
import org.unicam.intermediate.models.pojo.PhysicalPlace;
import org.unicam.intermediate.models.record.EnvironmentalTaskInfo;
import org.unicam.intermediate.service.participant.ParticipantDataService;
import org.unicam.intermediate.service.participant.ParticipantService;
import org.unicam.intermediate.service.participant.UserParticipantMappingService;
import org.unicam.intermediate.service.websocket.WebSocketSessionManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class EnvironmentalTaskRegistry {

    private static final Pattern GUARD_PATTERN = Pattern.compile(
            "^(.+?)\\.([A-Za-z0-9_-]+)\\s*(==|!=|>=|<=|>|<)\\s*(.+)$"
    );

    private static final String BPMN_ERROR_CODE_VAR = "__spaceBpmnErrorCode";
    private static final String BPMN_ERROR_MESSAGE_VAR = "__spaceBpmnErrorMessage";
    private static final String FAILED_ACTION_ERROR_CODE = "failedAction";

    private final EnvironmentDataService environmentDataService;
    private final RuntimeService runtimeService;
    private final ParticipantDataService participantDataService;
    private final ParticipantService participantService;
    private final UserParticipantMappingService userParticipantMappingService;
    private final WebSocketSessionManager webSocketSessionManager;

    // executionId -> active environmental task waiting for guard=true
    private final Map<String, EnvironmentalTaskInfo> activeEnvironmentalTasks = new ConcurrentHashMap<>();
    // executionId -> epoch millis when action timer started
    private final Map<String, Long> actionTimerStartByExecutionId = new ConcurrentHashMap<>();
    // executionId:action -> notification already sent (dedup)
    private final Map<String, Boolean> sentActionNotifications = new ConcurrentHashMap<>();

    public EnvironmentalTaskRegistry(EnvironmentDataService environmentDataService,
                                     RuntimeService runtimeService,
                                     ParticipantDataService participantDataService,
                                     ParticipantService participantService,
                                     UserParticipantMappingService userParticipantMappingService,
                                     WebSocketSessionManager webSocketSessionManager) {
        this.environmentDataService = environmentDataService;
        this.runtimeService = runtimeService;
        this.participantDataService = participantDataService;
        this.participantService = participantService;
        this.userParticipantMappingService = userParticipantMappingService;
        this.webSocketSessionManager = webSocketSessionManager;
    }

    public void registerTask(String executionId, String activityId, String guardExpression, String action, String participantId, Double timer) {
        if (executionId == null || activityId == null) {
            log.warn("[EnvironmentalRegistry] Cannot register task with null execution/activity id");
            return;
        }

        String normalizedGuard = guardExpression != null ? guardExpression.trim() : "";
        String normalizedAction = action != null ? action.trim() : "";
        EnvironmentalTaskInfo info = new EnvironmentalTaskInfo(executionId, activityId, normalizedGuard, normalizedAction, participantId, timer);
        activeEnvironmentalTasks.put(executionId, info);

        log.info("[EnvironmentalRegistry] Registered environmental task | activity={} | execution={} | guard='{}' | action='{}' | participantId={} | timer={}",
                activityId,
                executionId,
                normalizedGuard.isEmpty() ? "(empty)" : normalizedGuard,
            normalizedAction.isEmpty() ? "(empty)" : normalizedAction,
            participantId != null ? participantId : "(empty)",
            timer != null ? timer : "(empty)");
    }

    public void removeTask(String executionId) {
        if (executionId == null) {
            return;
        }

        EnvironmentalTaskInfo removed = activeEnvironmentalTasks.remove(executionId);
        actionTimerStartByExecutionId.remove(executionId);
        clearNotificationMarkersForExecution(executionId);
        if (removed != null) {
            log.info("[EnvironmentalRegistry] Removed environmental task | activity={} | execution={}",
                    removed.activityId(), removed.executionId());
        }
    }

    public void removeTask(String executionId, String expectedActivityId) {
        if (executionId == null || expectedActivityId == null) {
            return;
        }

        EnvironmentalTaskInfo currentTask = activeEnvironmentalTasks.get(executionId);
        if (currentTask == null || !Objects.equals(currentTask.activityId(), expectedActivityId)) {
            return;
        }

        if (activeEnvironmentalTasks.remove(executionId, currentTask)) {
            actionTimerStartByExecutionId.remove(executionId);
            clearNotificationMarkersForExecution(executionId);
            log.info("[EnvironmentalRegistry] Removed environmental task | activity={} | execution={}",
                    currentTask.activityId(), currentTask.executionId());
        }
    }

    /**
     * Returns action requests currently pending for the given participant.
     * A request is considered pending when:
     * 1) the task belongs to the participant,
     * 2) an action is configured,
     * 3) guard is already satisfied,
     * 4) action condition is still not satisfied.
     */
    public List<Map<String, String>> getPendingActionsForParticipant(String participantId) {
        if (participantId == null || participantId.isBlank()) {
            return List.of();
        }

        List<Map<String, String>> pendingActions = new ArrayList<>();

        for (EnvironmentalTaskInfo taskInfo : activeEnvironmentalTasks.values()) {
            if (!participantId.equals(taskInfo.participantId())) {
                continue;
            }

            String action = taskInfo.action();
            if (action == null || action.isBlank()) {
                continue;
            }

            boolean guardSatisfied = evaluateGuard(taskInfo.guardExpression(), taskInfo.activityId(), taskInfo.participantId(), taskInfo.executionId());
            if (!guardSatisfied) {
                continue;
            }

            boolean actionSatisfied = evaluateActionGuard(
                    action,
                    taskInfo.activityId(),
                    taskInfo.participantId(),
                    taskInfo.executionId(),
                    false
            );
            if (actionSatisfied) {
                continue;
            }

            Map<String, String> actionView = new LinkedHashMap<>();
            actionView.put("executionId", taskInfo.executionId());
            actionView.put("activityId", taskInfo.activityId());
            actionView.put("action", action);
            actionView.put("message", toHumanReadableAction(action));
            pendingActions.add(actionView);
        }

        return pendingActions;
    }

    @Scheduled(fixedRate = 2000)
    public void checkEnvironmentalGuards() {
        if (activeEnvironmentalTasks.isEmpty()) {
            return;
        }

        List<EnvironmentalTaskInfo> completedTasks = new ArrayList<>();

        for (EnvironmentalTaskInfo taskInfo : activeEnvironmentalTasks.values()) {
            if (!executionExists(taskInfo.executionId())) {
                log.warn("[EnvironmentalRegistry] Execution {} not found for activity {} | removing stale environmental task",
                        taskInfo.executionId(), taskInfo.activityId());
                completedTasks.add(taskInfo);
                continue;
            }

            boolean guardSatisfied = evaluateGuard(taskInfo.guardExpression(), taskInfo.activityId(), taskInfo.participantId(), taskInfo.executionId());
            if (!guardSatisfied) {
                continue;
            }

            ActionCheckResult actionCheckResult = evaluateActionWithOptionalTimer(taskInfo);
            if (actionCheckResult == ActionCheckResult.WAIT) {
                continue;
            }

            try {
                if (actionCheckResult == ActionCheckResult.TIMEOUT) {
                    runtimeService.setVariableLocal(taskInfo.executionId(), BPMN_ERROR_CODE_VAR, FAILED_ACTION_ERROR_CODE);
                    runtimeService.setVariableLocal(
                            taskInfo.executionId(),
                            BPMN_ERROR_MESSAGE_VAR,
                            String.format("Action '%s' did not become true within timer for activity '%s'",
                                    taskInfo.action(), taskInfo.activityId())
                    );
                }

                runtimeService.signal(taskInfo.executionId());
                completedTasks.add(taskInfo);
                if (actionCheckResult == ActionCheckResult.TIMEOUT) {
                    log.warn("[EnvironmentalRegistry] Action timer expired -> raised '{}' | activity={} | execution={} | action='{}' | timer={}",
                            FAILED_ACTION_ERROR_CODE,
                            taskInfo.activityId(),
                            taskInfo.executionId(),
                            taskInfo.action(),
                            taskInfo.timer());
                } else {
                    log.info("[EnvironmentalRegistry] Guard+Action satisfied -> task completed | activity={} | execution={} | guard='{}' | action='{}'",
                        taskInfo.activityId(),
                        taskInfo.executionId(),
                        taskInfo.guardExpression(),
                        taskInfo.action());
                }
            } catch (Exception e) {
                if (isExecutionNotFoundError(e)) {
                    log.warn("[EnvironmentalRegistry] Execution {} already ended for activity {}. Cleaning up stale task.",
                            taskInfo.executionId(), taskInfo.activityId());
                    completedTasks.add(taskInfo);
                    continue;
                }
                log.error("[EnvironmentalRegistry] Failed to signal execution {} for activity {}: {}",
                        taskInfo.executionId(), taskInfo.activityId(), e.getMessage(), e);
            }
        }

        completedTasks.forEach(task -> removeTask(task.executionId(), task.activityId()));
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

    private boolean evaluateGuard(String guardExpression, String activityId, String participantId, String executionId) {
        if (guardExpression == null || guardExpression.isBlank()) {
            log.info("[ENVIRONMENTAL] Empty guard expression for activity {} -> auto-pass", activityId);
            return true;
        }

        String resolvedExpression = resolveMyPlace(guardExpression, participantId, activityId);
        if (guardExpression.contains("myPlace()") && resolvedExpression.contains("myPlace()")) {
            log.debug("[ENVIRONMENTAL] Waiting for participant position to resolve myPlace() for activity {} | participantId={}",
                    activityId, participantId);
            return false;
        }

        Matcher matcher = GUARD_PATTERN.matcher(resolvedExpression.trim());
        if (!matcher.matches()) {
            log.warn("[ENVIRONMENTAL] Invalid guard format for activity {}: '{}'", activityId, guardExpression);
            return false;
        }

        String reference = matcher.group(1).trim();
        String attributeKey = matcher.group(2);
        String operator = matcher.group(3);
        String expectedRaw = unquote(matcher.group(4).trim());

        // participant.position == placeRef notation
        if ("position".equals(attributeKey)) {
            return evaluateParticipantPosition(reference, operator, expectedRaw, activityId, executionId);
        }

        Optional<String> logicalPlaceId = environmentDataService.resolveLogicalPlaceId(reference);
        if (logicalPlaceId.isPresent()) {
            Optional<Object> logicalGuardValue = resolveLogicalPlaceGuardValue(logicalPlaceId.get(), attributeKey);
            if (logicalGuardValue.isPresent()) {
                boolean result = compare(logicalGuardValue.get(), operator, expectedRaw);
                log.debug("[ENVIRONMENTAL] Logical guard evaluation | activity={} | logical='{}' | attr='{}' | actual='{}' | op='{}' | expected='{}' -> {}",
                        activityId, logicalPlaceId.get(), attributeKey, logicalGuardValue.get(), operator, expectedRaw, result);
                return result;
            }
        }

        Optional<Object> actualValueOpt = environmentDataService.getPhysicalPlaceAttribute(reference, attributeKey);
        if (actualValueOpt.isEmpty()) {
            log.debug("[ENVIRONMENTAL] Attribute '{}.{}' not found for activity {}", reference, attributeKey, activityId);
            return false;
        }

        Object actualValue = actualValueOpt.get();
        boolean result = compare(actualValue, operator, expectedRaw);

        log.debug("[ENVIRONMENTAL] Guard evaluation | activity={} | expr='{}' | actual='{}' -> {}",
                activityId, resolvedExpression, actualValue, result);

        return result;
    }

    private boolean evaluateParticipantPosition(String participantRef, String operator, String expectedPlaceRef, String activityId, String executionId) {
        String resolvedParticipantRef = resolveParticipantReference(executionId, participantRef);

        Optional<String> currentPositionOpt = participantDataService.getParticipant(resolvedParticipantRef)
                .map(p -> p.getPosition());

        if (currentPositionOpt.isEmpty() || currentPositionOpt.get() == null) {
            log.debug("[ENVIRONMENTAL] Position check: participant '{}' not found or has no position for activity {}",
                    participantRef, activityId);
            return false;
        }

        String currentPosition = currentPositionOpt.get();

        Optional<String> resolvedLogicalExpected = environmentDataService.resolveLogicalPlaceId(expectedPlaceRef);
        if (resolvedLogicalExpected.isPresent()) {
            boolean inLogicalPlace = environmentDataService.isPhysicalPlaceInLogicalPlace(currentPosition, resolvedLogicalExpected.get());
            boolean result = switch (operator) {
                case "==" -> inLogicalPlace;
                case "!=" -> !inLogicalPlace;
                default -> false;
            };

            log.debug("[ENVIRONMENTAL] Position guard (logical) | activity={} | participant='{}' | position='{}' | op='{}' | expected='{}' -> {}",
                    activityId, resolvedParticipantRef, currentPosition, operator, resolvedLogicalExpected.get(), result);

            return result;
        }

        // Resolve right-hand side place reference to canonical id (by id or name)
        String resolvedExpected = environmentDataService.resolvePhysicalPlaceId(expectedPlaceRef)
                .or(() -> environmentDataService.resolveLogicalPlaceId(expectedPlaceRef))
                .orElse(expectedPlaceRef);

        // Resolve participant's current position to canonical id as well
        String resolvedPosition = environmentDataService.resolvePhysicalPlaceId(currentPosition)
                .orElse(currentPosition);

        boolean result = compare(resolvedPosition, operator, resolvedExpected);

        log.debug("[ENVIRONMENTAL] Position guard | activity={} | participant='{}' | position='{}' | op='{}' | expected='{}' -> {}",
                activityId, resolvedParticipantRef, resolvedPosition, operator, resolvedExpected, result);

        return result;
    }

    private String resolveParticipantReference(String executionId, String participantRef) {
        if (participantRef == null || participantRef.isBlank() || executionId == null || executionId.isBlank()) {
            return participantRef;
        }

        try {
            Execution execution = runtimeService.createExecutionQuery()
                    .executionId(executionId)
                    .singleResult();
            if (execution == null || execution.getProcessInstanceId() == null) {
                return participantRef;
            }

            ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(execution.getProcessInstanceId())
                    .singleResult();
            if (processInstance == null || processInstance.getProcessDefinitionId() == null) {
                return participantRef;
            }

            return participantService.resolveParticipantId(processInstance.getProcessDefinitionId(), participantRef);
        } catch (Exception e) {
            log.debug("[ENVIRONMENTAL] Could not resolve participant reference '{}' for execution {}",
                    participantRef, executionId, e);
            return participantRef;
        }
    }

    private String resolveMyPlace(String expression, String participantId, String activityId) {
        if (expression == null || !expression.contains("myPlace()")) {
            return expression;
        }
        if (participantId == null) {
            log.warn("[ENVIRONMENTAL] myPlace() used but no participantId available for activity {}", activityId);
            return expression;
        }
        return participantDataService.getParticipant(participantId)
                .map(p -> {
                    String pos = p.getPosition();
                    if (pos == null) {
                        log.warn("[ENVIRONMENTAL] myPlace() used but participant '{}' has no position for activity {}", participantId, activityId);
                        return expression;
                    }
                    log.debug("[ENVIRONMENTAL] Resolved myPlace() -> '{}' for participant '{}' activity {}",
                            pos, participantId, activityId);
                    return expression.replace("myPlace()", pos);
                })
                .orElseGet(() -> {
                    log.warn("[ENVIRONMENTAL] myPlace() used but participant '{}' not found for activity {}", participantId, activityId);
                    return expression;
                });
    }

    private boolean compare(Object actualValue, String operator, String expectedRaw) {
        if (actualValue == null) {
            return false;
        }

        String actualText = String.valueOf(actualValue).trim();

        Double actualNumber = toNumber(actualText);
        Double expectedNumber = toNumber(expectedRaw);

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
            case "==" -> actualText.equals(expectedRaw);
            case "!=" -> !actualText.equals(expectedRaw);
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

    private String unquote(String raw) {
        if ((raw.startsWith("\"") && raw.endsWith("\"")) || (raw.startsWith("'") && raw.endsWith("'"))) {
            return raw.substring(1, raw.length() - 1).trim();
        }
        return raw;
    }

    private Optional<Object> resolveLogicalPlaceGuardValue(String logicalPlaceId, String attributeKey) {
        if (attributeKey == null || attributeKey.isBlank()) {
            return Optional.empty();
        }

        int count = environmentDataService.countPhysicalPlacesInLogicalPlace(logicalPlaceId);
        return switch (attributeKey.trim().toLowerCase()) {
            case "empty", "isempty" -> Optional.of(count == 0);
            case "nonempty", "isnotempty" -> Optional.of(count > 0);
            case "count", "size" -> Optional.of(count);
            default -> Optional.empty();
        };
    }

    private boolean evaluateActionGuard(String action,
                                        String activityId,
                                        String participantId,
                                        String executionId,
                                        boolean notifyParticipant) {
        if (action == null || action.isBlank()) {
            log.debug("[ENVIRONMENTAL] Empty action for activity {} -> action-check auto-pass", activityId);
            return true;
        }

        return switch (action.trim().toLowerCase()) {
            case "turnlightson" -> turnLightsOn(activityId, participantId, executionId, notifyParticipant);
            case "turnlightsoff" -> turnLightsOff(activityId, participantId, executionId, notifyParticipant);
            case "cleanroom" -> cleanRoom(activityId, participantId, executionId, notifyParticipant);
            case "checkroom" -> checkRoom(activityId, participantId, executionId, notifyParticipant);
            case "checkvehicle" -> checkVehicle(activityId, participantId, executionId, notifyParticipant);
            case "fixvehicle" -> fixVehicle(activityId, participantId, executionId, notifyParticipant);
            case "assessalldirty" -> assessAllDirty(activityId, executionId, notifyParticipant);
            case "startemergency" -> startEmergency(activityId, executionId, notifyParticipant);
            case "endemergency" -> endEmergency(activityId, executionId, notifyParticipant);
            case "extinguishfire" -> extinguishFire(activityId, participantId, executionId, notifyParticipant);
            case "preparearea" -> prepareArea(activityId, participantId, executionId, notifyParticipant);
            case "plantarea" -> plantArea(activityId, participantId, executionId, notifyParticipant);
            case "waterarea" -> waterArea(activityId, participantId, executionId, notifyParticipant);
            case "leaveroom" -> leaveRoom(activityId, participantId, notifyParticipant);
            case "occupyroom" -> occupyRoom(activityId, participantId, notifyParticipant);
            case "rentvehicle" -> rentVehicle(activityId, participantId, notifyParticipant);
            case "leavevehicle" -> leaveVehicle(activityId, participantId, notifyParticipant);
            default -> {
                log.warn("[ENVIRONMENTAL] Unknown action '{}' for activity {} -> action-check fails", action, activityId);
                yield false;
            }
        };
    }

    /**
     * Dedicated action handler for turning lights on.
     * Notification and guard evaluation are intentionally separate and ordered.
     */
    private boolean turnLightsOn(String activityId, String participantId, String executionId, boolean notifyParticipant) {
        if (notifyParticipant) {
            notifyTurnLightsOn(executionId, participantId);
        }
        return isTurnLightsOnSatisfied(activityId, participantId);
    }

    /**
     * Dedicated action handler for turning lights off.
     * Notification and guard evaluation are intentionally separate and ordered.
     */
    private boolean turnLightsOff(String activityId, String participantId, String executionId, boolean notifyParticipant) {
        if (notifyParticipant) {
            notifyTurnLightsOff(executionId, participantId);
        }
        return isTurnLightsOffSatisfied(activityId, participantId);
    }

    /**
     * Dedicated action handler for room cleaning.
     * Notification and guard evaluation are intentionally separate and ordered.
     */
    private boolean cleanRoom(String activityId, String participantId, String executionId, boolean notifyParticipant) {
        if (notifyParticipant) {
            notifyCleanRoom(executionId, participantId);
        }
        return isCleanRoomSatisfied(activityId, participantId);
    }

    /**
     * Dedicated action handler for room checking.
     * Notification and guard evaluation are intentionally separate and ordered.
     */
    private boolean checkRoom(String activityId, String participantId, String executionId, boolean notifyParticipant) {
        if (notifyParticipant) {
            notifyCheckRoom(executionId, participantId);
        }
        return isCheckRoomSatisfied(activityId, participantId);
    }

    /**
     * Dedicated action handler for vehicle status check.
     * Notification and guard evaluation are intentionally separate and ordered.
     */
    private boolean checkVehicle(String activityId, String participantId, String executionId, boolean notifyParticipant) {
        if (notifyParticipant) {
            notifyCheckVehicle(executionId, participantId);
        }
        return isCheckVehicleSatisfied(activityId, participantId);
    }

    /**
     * Dedicated action handler for vehicle fixing.
     * Notification and guard evaluation are intentionally separate and ordered.
     */
    private boolean fixVehicle(String activityId, String participantId, String executionId, boolean notifyParticipant) {
        if (notifyParticipant) {
            notifyFixVehicle(executionId, participantId);
        }
        return isFixVehicleSatisfied(activityId, participantId);
    }

    /**
     * Dedicated action handler for area preparation before planting.
     * Notification and guard evaluation are intentionally separate and ordered.
     */
    private boolean prepareArea(String activityId, String participantId, String executionId, boolean notifyParticipant) {
        if (notifyParticipant) {
            notifyPrepareArea(executionId, participantId);
        }
        return isPrepareAreaSatisfied(activityId, participantId);
    }

    private void notifyTurnLightsOn(String executionId, String participantId) {
        notifyParticipantAction(executionId, participantId, "turnLightsOn", "Turn the lights on");
    }

    private boolean isTurnLightsOnSatisfied(String activityId, String participantId) {
        return evaluateGuard("myPlace().lights == on", activityId + "#action:turnLightsOn", participantId, null);
    }

    private void notifyTurnLightsOff(String executionId, String participantId) {
        notifyParticipantAction(executionId, participantId, "turnLightsOff", "Turn the lights off");
    }

    private boolean isTurnLightsOffSatisfied(String activityId, String participantId) {
        return evaluateGuard("myPlace().lights == off", activityId + "#action:turnLightsOff", participantId, null);
    }

    private void notifyCleanRoom(String executionId, String participantId) {
        notifyParticipantAction(executionId, participantId, "cleanRoom", "Clean the room");
    }

    private boolean isCleanRoomSatisfied(String activityId, String participantId) {
        return evaluateGuard("myPlace().state == clean", activityId + "#action:cleanRoom", participantId, null);
    }

    private void notifyCheckRoom(String executionId, String participantId) {
        notifyParticipantAction(executionId, participantId, "checkRoom", "Check the room");
    }

    private boolean isCheckRoomSatisfied(String activityId, String participantId) {
        return evaluateGuard("myPlace().state != null", activityId + "#action:checkRoom", participantId, null);
    }

    private void notifyCheckVehicle(String executionId, String participantId) {
        notifyParticipantAction(executionId, participantId, "checkVehicle", "Report the vehicle state");
    }

    private boolean isCheckVehicleSatisfied(String activityId, String participantId) {
        return evaluateGuard("myPlace().state != null", activityId + "#action:checkVehicle", participantId, null);
    }

    private void notifyFixVehicle(String executionId, String participantId) {
        notifyParticipantAction(executionId, participantId, "fixVehicle", "Fix the vehicle");
    }

    private boolean isFixVehicleSatisfied(String activityId, String participantId) {
        return evaluateGuard("myPlace().state == available", activityId + "#action:fixVehicle", participantId, null);
    }

    /**
     * Dedicated action handler for fire extinguishing.
     * Notification and guard evaluation are intentionally separate and ordered.
     */
    private boolean extinguishFire(String activityId, String participantId, String executionId, boolean notifyParticipant) {
        if (notifyParticipant) {
            notifyExtinguishFire(executionId, participantId);
        }
        return isExtinguishFireSatisfied(activityId, participantId);
    }

    private void notifyExtinguishFire(String executionId, String participantId) {
        notifyParticipantAction(executionId, participantId, "extinguishFire", "Extinguish the fire");
    }

    private boolean isExtinguishFireSatisfied(String activityId, String participantId) {
        return evaluateGuard("myPlace().fire == false", activityId + "#action:extinguishFire", participantId, null);
    }

    /**
     * assessAllDirty is a system action with no participant notification.
     * Execute mode marks state=dirty for place1..place16 and completes immediately.
     */
    private boolean assessAllDirty(String activityId, String executionId, boolean execute) {
        if (!execute) {
            return true;
        }

        int updated = 0;
        for (int i = 1; i <= 16; i++) {
            String placeId = "place" + i;
            environmentDataService.getPhysicalPlace(placeId).ifPresent(place -> {
                if (place.getAttributes() != null) {
                    place.getAttributes().put("state", "dirty");
                }
            });

            if (environmentDataService.getPhysicalPlace(placeId)
                    .map(p -> p.getAttributes() != null && "dirty".equals(String.valueOf(p.getAttributes().get("state"))))
                    .orElse(false)) {
                updated++;
            }
        }

        log.info("[ENVIRONMENTAL] assessAllDirty executed | activity={} | execution={} | updatedPlaces={}",
                activityId, executionId, updated);
        return true;
    }

    /**
     * startEmergency is a system action with no participant notification.
     * Execute mode sets alarm=true and emergency=true for all physical places that currently
     * belong to the logical place "Fire Areas", then completes immediately.
     */
    private boolean startEmergency(String activityId, String executionId, boolean execute) {
        if (!execute) {
            return true;
        }

        Optional<String> fireAreasLogicalIdOpt = environmentDataService.resolveLogicalPlaceId("Fire Areas");
        if (fireAreasLogicalIdOpt.isEmpty()) {
            log.warn("[ENVIRONMENTAL] startEmergency executed but logical place 'Fire Areas' was not found | activity={} | execution={}",
                    activityId, executionId);
            return true;
        }

        String fireAreasLogicalId = fireAreasLogicalIdOpt.get();
        int matchedPlaces = 0;
        int updatedPlaces = 0;

        for (PhysicalPlace place : environmentDataService.getPhysicalPlaces()) {
            if (place == null || place.getId() == null || place.getId().isBlank()) {
                continue;
            }

            if (!environmentDataService.isPhysicalPlaceInLogicalPlace(place.getId(), fireAreasLogicalId)) {
                continue;
            }

            matchedPlaces++;
            if (place.getAttributes() == null) {
                log.debug("[ENVIRONMENTAL] startEmergency: place '{}' has no attributes map - skipping", place.getId());
                continue;
            }

            place.getAttributes().put("alarm", true);
            place.getAttributes().put("emergency", true);
            updatedPlaces++;
        }

        log.info("[ENVIRONMENTAL] startEmergency executed | activity={} | execution={} | logicalPlace='{}' | matchedPlaces={} | updatedPlaces={} | alarm=true | emergency=true",
                activityId, executionId, fireAreasLogicalId, matchedPlaces, updatedPlaces);
        return true;
    }

    /**
     * endEmergency is a system action with no participant notification.
     * Execute mode sets alarm=false for all physical places that currently belong
     * to the logical place "Emergency Areas", then completes immediately.
     */
    private boolean endEmergency(String activityId, String executionId, boolean execute) {
        if (!execute) {
            return true;
        }

        Optional<String> emergencyAreasLogicalIdOpt = environmentDataService.resolveLogicalPlaceId("Emergency Areas");
        if (emergencyAreasLogicalIdOpt.isEmpty()) {
            log.warn("[ENVIRONMENTAL] endEmergency executed but logical place 'Emergency Areas' was not found | activity={} | execution={}",
                    activityId, executionId);
            return true;
        }

        String emergencyAreasLogicalId = emergencyAreasLogicalIdOpt.get();
        int matchedPlaces = 0;
        int updatedPlaces = 0;

        for (PhysicalPlace place : environmentDataService.getPhysicalPlaces()) {
            if (place == null || place.getId() == null || place.getId().isBlank()) {
                continue;
            }

            if (!environmentDataService.isPhysicalPlaceInLogicalPlace(place.getId(), emergencyAreasLogicalId)) {
                continue;
            }

            matchedPlaces++;
            if (place.getAttributes() == null) {
                log.debug("[ENVIRONMENTAL] endEmergency: place '{}' has no attributes map - skipping", place.getId());
                continue;
            }

            place.getAttributes().put("alarm", false);
            updatedPlaces++;
        }

        log.info("[ENVIRONMENTAL] endEmergency executed | activity={} | execution={} | logicalPlace='{}' | matchedPlaces={} | updatedPlaces={} | alarm=false",
                activityId, executionId, emergencyAreasLogicalId, matchedPlaces, updatedPlaces);
        return true;
    }

    private void notifyPrepareArea(String executionId, String participantId) {
        notifyParticipantAction(executionId, participantId, "prepareArea", "Prepare the area for planting");
    }

    private boolean isPrepareAreaSatisfied(String activityId, String participantId) {
        return evaluateGuard("myPlace().state == crop", activityId + "#action:prepareArea", participantId, null);
    }

    /**
     * Dedicated action handler for planting the current area.
     * Notification and guard evaluation are intentionally separate and ordered.
     */
    private boolean plantArea(String activityId, String participantId, String executionId, boolean notifyParticipant) {
        if (notifyParticipant) {
            notifyPlantArea(executionId, participantId);
        }
        return isPlantAreaSatisfied(activityId, participantId);
    }

    private void notifyPlantArea(String executionId, String participantId) {
        notifyParticipantAction(executionId, participantId, "plantArea", "Plant in that area");
    }

    private boolean isPlantAreaSatisfied(String activityId, String participantId) {
        return evaluateGuard("myPlace().state == planted", activityId + "#action:plantArea", participantId, null);
    }

    /**
     * Dedicated action handler for watering the current area.
     * Notification and guard evaluation are intentionally separate and ordered.
     */
    private boolean waterArea(String activityId, String participantId, String executionId, boolean notifyParticipant) {
        if (notifyParticipant) {
            notifyWaterArea(executionId, participantId);
        }
        return isWaterAreaSatisfied(activityId, participantId);
    }

    private void notifyWaterArea(String executionId, String participantId) {
        notifyParticipantAction(executionId, participantId, "waterArea", "Water that area");
    }

    private boolean isWaterAreaSatisfied(String activityId, String participantId) {
        return evaluateGuard("myPlace().dry == false", activityId + "#action:waterArea", participantId, null);
    }

    /**
     * leaveRoom is a system action.
     * Read-only checks must not mutate state; execution clears the current place state on a
     * best-effort basis and always completes the task.
     */
    private boolean leaveRoom(String activityId, String participantId, boolean execute) {
        if (!execute) {
            log.debug("[ENVIRONMENTAL] leaveRoom check-only (no side effects) | activity={} | participant={}",
                    activityId, participantId);
            return true;
        }

        // Execute path — best-effort cleanup, always returns true.
        if (participantId == null || participantId.isBlank()) {
            log.warn("[ENVIRONMENTAL] leaveRoom: participantId missing for activity {} — completing without cleanup", activityId);
            return true;
        }

        String placeRef = participantDataService.getParticipant(participantId)
                .map(p -> p.getPosition())
                .orElse(null);

        if (placeRef != null && !placeRef.isBlank()) {
            String placeId = environmentDataService.resolvePhysicalPlaceId(placeRef).orElse(placeRef);
            environmentDataService.getPhysicalPlace(placeId).ifPresentOrElse(
                    place -> {
                        if (place.getAttributes() != null) {
                            place.getAttributes().put("state", null);
                            place.getAttributes().put("occupied", false);
                            log.info("[ENVIRONMENTAL] leaveRoom executed | activity={} | participant={} | place={} | state=null | occupied=false",
                                    activityId, participantId, place.getId());
                        } else {
                            log.debug("[ENVIRONMENTAL] leaveRoom: place '{}' has no attributes map — skipping | activity={}",
                                    placeId, activityId);
                        }
                    },
                    () -> log.info("[ENVIRONMENTAL] leaveRoom: place '{}' not found — skipping | activity={} | participant={}",
                            placeId, activityId, participantId)
            );
        } else {
            log.info("[ENVIRONMENTAL] leaveRoom: participant '{}' has no current position — skipping state update | activity={}",
                    participantId, activityId);
        }

        return true;
    }

    /**
     * occupyRoom is a system action.
     * Check-only mode has no side effects and always returns true.
     * Execute mode sets occupied=true on the participant's current place.
     */
    private boolean occupyRoom(String activityId, String participantId, boolean execute) {
        if (!execute) {
            log.debug("[ENVIRONMENTAL] occupyRoom check-only (no side effects) | activity={} | participant={}",
                    activityId, participantId);
            return true;
        }

        if (participantId == null || participantId.isBlank()) {
            log.warn("[ENVIRONMENTAL] occupyRoom: participantId missing for activity {} — completing without update", activityId);
            return true;
        }

        String placeRef = participantDataService.getParticipant(participantId)
                .map(p -> p.getPosition())
                .orElse(null);

        if (placeRef != null && !placeRef.isBlank()) {
            String placeId = environmentDataService.resolvePhysicalPlaceId(placeRef).orElse(placeRef);
            environmentDataService.getPhysicalPlace(placeId).ifPresentOrElse(
                    place -> {
                        if (place.getAttributes() != null) {
                            place.getAttributes().put("occupied", true);
                            log.info("[ENVIRONMENTAL] occupyRoom executed | activity={} | participant={} | place={} | occupied=true",
                                    activityId, participantId, place.getId());
                        } else {
                            log.debug("[ENVIRONMENTAL] occupyRoom: place '{}' has no attributes map — skipping | activity={}",
                                    placeId, activityId);
                        }
                    },
                    () -> log.info("[ENVIRONMENTAL] occupyRoom: place '{}' not found — skipping | activity={} | participant={}",
                            placeId, activityId, participantId)
            );
        } else {
            log.info("[ENVIRONMENTAL] occupyRoom: participant '{}' has no current position — skipping | activity={}",
                    participantId, activityId);
        }

        return true;
    }

    /**
     * rentVehicle is a system action.
     * Check-only mode has no side effects and always returns true.
        * Execute mode sets taken=false and state=null on the participant's current place.
     */
    private boolean rentVehicle(String activityId, String participantId, boolean execute) {
        if (!execute) {
            log.debug("[ENVIRONMENTAL] rentVehicle check-only (no side effects) | activity={} | participant={}",
                    activityId, participantId);
            return true;
        }

        if (participantId == null || participantId.isBlank()) {
            log.warn("[ENVIRONMENTAL] rentVehicle: participantId missing for activity {} — completing without update", activityId);
            return true;
        }

        String placeRef = participantDataService.getParticipant(participantId)
                .map(p -> p.getPosition())
                .orElse(null);

        if (placeRef != null && !placeRef.isBlank()) {
            String placeId = environmentDataService.resolvePhysicalPlaceId(placeRef).orElse(placeRef);
            environmentDataService.getPhysicalPlace(placeId).ifPresentOrElse(
                    place -> {
                        if (place.getAttributes() != null) {
                            place.getAttributes().put("taken", false);
                            place.getAttributes().put("state", null);
                            log.info("[ENVIRONMENTAL] rentVehicle executed | activity={} | participant={} | place={} | taken=false | state=null",
                                    activityId, participantId, place.getId());
                        } else {
                            log.debug("[ENVIRONMENTAL] rentVehicle: place '{}' has no attributes map — skipping | activity={}",
                                    placeId, activityId);
                        }
                    },
                    () -> log.info("[ENVIRONMENTAL] rentVehicle: place '{}' not found — skipping | activity={} | participant={}",
                            placeId, activityId, participantId)
            );
        } else {
            log.info("[ENVIRONMENTAL] rentVehicle: participant '{}' has no current position — skipping | activity={}",
                    participantId, activityId);
        }

        return true;
    }

    /**
     * leaveVehicle is a system action.
     * Check-only mode has no side effects and always returns true.
     * Execute mode sets taken=true on the participant's current place.
     */
    private boolean leaveVehicle(String activityId, String participantId, boolean execute) {
        if (!execute) {
            log.debug("[ENVIRONMENTAL] leaveVehicle check-only (no side effects) | activity={} | participant={}",
                    activityId, participantId);
            return true;
        }

        if (participantId == null || participantId.isBlank()) {
            log.warn("[ENVIRONMENTAL] leaveVehicle: participantId missing for activity {} — completing without update", activityId);
            return true;
        }

        String placeRef = participantDataService.getParticipant(participantId)
                .map(p -> p.getPosition())
                .orElse(null);

        if (placeRef != null && !placeRef.isBlank()) {
            String placeId = environmentDataService.resolvePhysicalPlaceId(placeRef).orElse(placeRef);
            environmentDataService.getPhysicalPlace(placeId).ifPresentOrElse(
                    place -> {
                        if (place.getAttributes() != null) {
                            place.getAttributes().put("taken", true);
                            log.info("[ENVIRONMENTAL] leaveVehicle executed | activity={} | participant={} | place={} | taken=true",
                                    activityId, participantId, place.getId());
                        } else {
                            log.debug("[ENVIRONMENTAL] leaveVehicle: place '{}' has no attributes map — skipping | activity={}",
                                    placeId, activityId);
                        }
                    },
                    () -> log.info("[ENVIRONMENTAL] leaveVehicle: place '{}' not found — skipping | activity={} | participant={}",
                            placeId, activityId, participantId)
            );
        } else {
            log.info("[ENVIRONMENTAL] leaveVehicle: participant '{}' has no current position — skipping | activity={}",
                    participantId, activityId);
        }

        return true;
    }

    private void notifyParticipantAction(String executionId,
                                         String participantId,
                                         String actionKey,
                                         String message) {
        if (executionId == null || executionId.isBlank() || participantId == null || participantId.isBlank()) {
            return;
        }

        String dedupKey = executionId + ":" + actionKey.toLowerCase();
        if (sentActionNotifications.putIfAbsent(dedupKey, Boolean.TRUE) != null) {
            return;
        }

        String businessKey = resolveBusinessKeyForExecution(executionId);
        if (businessKey == null || businessKey.isBlank()) {
            log.warn("[ENVIRONMENTAL] Cannot notify participant '{}' for action '{}' because businessKey is unavailable (execution={})",
                    participantId, actionKey, executionId);
            return;
        }

        String userId = userParticipantMappingService.getUserForParticipant(businessKey, participantId);
        if (userId == null || userId.isBlank()) {
            log.info("[ENVIRONMENTAL] Participant '{}' has no mapped user for businessKey '{}' (action='{}')",
                    participantId, businessKey, actionKey);
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("executionId", executionId);
        payload.put("participantId", participantId);
        payload.put("action", actionKey);
        payload.put("message", message);

        webSocketSessionManager.broadcastToUser(
                userId,
                GpsResponse.success("ACTION_REQUIRED", message, payload),
                null
        );

        log.info("[ENVIRONMENTAL] Sent action notification | userId={} | participantId={} | action={} | execution={}",
                userId, participantId, actionKey, executionId);
    }

    private String resolveBusinessKeyForExecution(String executionId) {
        try {
            Execution execution = runtimeService.createExecutionQuery()
                    .executionId(executionId)
                    .singleResult();
            if (execution == null || execution.getProcessInstanceId() == null) {
                return null;
            }

            ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(execution.getProcessInstanceId())
                    .singleResult();
            return processInstance != null ? processInstance.getBusinessKey() : null;
        } catch (Exception e) {
            log.warn("[ENVIRONMENTAL] Failed to resolve businessKey for execution {}: {}", executionId, e.getMessage());
            return null;
        }
    }

    private void clearNotificationMarkersForExecution(String executionId) {
        if (executionId == null || executionId.isBlank()) {
            return;
        }
        String prefix = executionId + ":";
        for (String key : new HashSet<>(sentActionNotifications.keySet())) {
            if (key.startsWith(prefix)) {
                sentActionNotifications.remove(key);
            }
        }
    }

    private String toHumanReadableAction(String action) {
        if (action == null || action.isBlank()) {
            return "Action not specified";
        }

        return switch (action.trim().toLowerCase()) {
            case "turnlightson" -> "Turn the lights on";
            case "turnlightsoff" -> "Turn the lights off";
            case "cleanroom" -> "Clean the room";
            case "checkroom" -> "Check the room";
            case "checkvehicle" -> "Report the vehicle state";
            case "fixvehicle" -> "Fix the vehicle";
            case "assessalldirty" -> "Assess all rooms as dirty";
            case "startemergency" -> "Start emergency";
            case "endemergency" -> "End emergency";
            case "extinguishfire" -> "Extinguish the fire";
            case "preparearea" -> "Prepare the area for planting";
            case "plantarea" -> "Plant in that area";
            case "waterarea" -> "Water that area";
            case "leaveroom" -> "Leave the room";
            case "occupyroom" -> "Occupy the room";
            case "rentvehicle" -> "Rent the vehicle";
            case "leavevehicle" -> "Leave the vehicle";
            default -> "Execute action: " + action;
        };
    }

    private ActionCheckResult evaluateActionWithOptionalTimer(EnvironmentalTaskInfo taskInfo) {
        String action = taskInfo.action();
        String executionId = taskInfo.executionId();
        String activityId = taskInfo.activityId();

        // Timer is meaningful only when an action is configured.
        if (action == null || action.isBlank()) {
            actionTimerStartByExecutionId.remove(executionId);
            return ActionCheckResult.SATISFIED;
        }

        boolean actionSatisfied = evaluateActionGuard(
                action,
                activityId,
                taskInfo.participantId(),
                executionId,
                true
        );
        if (actionSatisfied) {
            actionTimerStartByExecutionId.remove(executionId);
            clearNotificationMarkersForExecution(executionId);
            return ActionCheckResult.SATISFIED;
        }

        Double timerSeconds = taskInfo.timer();
        if (timerSeconds == null || timerSeconds <= 0) {
            return ActionCheckResult.WAIT;
        }

        long startTime = actionTimerStartByExecutionId.computeIfAbsent(executionId, ignored -> {
            long now = System.currentTimeMillis();
            log.info("[EnvironmentalRegistry] Action timer started | activity={} | execution={} | action='{}' | timer={}s",
                    activityId, executionId, action, timerSeconds);
            return now;
        });

        long timeoutMillis = Math.round(timerSeconds * 1000.0d);
        long elapsed = System.currentTimeMillis() - startTime;

        if (elapsed >= timeoutMillis) {
            actionTimerStartByExecutionId.remove(executionId);
            return ActionCheckResult.TIMEOUT;
        }

        return ActionCheckResult.WAIT;
    }

    private enum ActionCheckResult {
        SATISFIED,
        WAIT,
        TIMEOUT
    }
}
