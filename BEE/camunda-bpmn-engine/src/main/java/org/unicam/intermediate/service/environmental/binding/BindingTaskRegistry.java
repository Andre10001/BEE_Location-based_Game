package org.unicam.intermediate.service.environmental.binding;

import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.RuntimeService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.unicam.intermediate.models.record.BindingTaskInfo;
import org.unicam.intermediate.service.participant.ParticipantDataService;
import org.unicam.intermediate.service.environmental.BoundParticipantsDiscordanceMonitor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class BindingTaskRegistry {

    private static final String BPMN_ERROR_CODE_VAR = "__spaceBpmnErrorCode";
    private static final String BPMN_ERROR_MESSAGE_VAR = "__spaceBpmnErrorMessage";
    private static final String FAILED_BINDING_ERROR_CODE = "failedBinding";

    private final ParticipantDataService participantDataService;
    private final RuntimeService runtimeService;
    private final BoundParticipantsDiscordanceMonitor boundParticipantsMonitor;

    // participantId -> binding task currently waiting for counterpart
    private final Map<String, BindingTaskInfo> waitingByParticipant = new ConcurrentHashMap<>();

    // pairKey -> active paired binding tasks to monitor by position
    private final Map<String, BindingPair> activePairs = new ConcurrentHashMap<>();

    // executionId -> epoch millis when binding timer started
    private final Map<String, Long> bindingTimerStartByExecutionId = new ConcurrentHashMap<>();

    public BindingTaskRegistry(ParticipantDataService participantDataService,
                               RuntimeService runtimeService,
                               BoundParticipantsDiscordanceMonitor boundParticipantsMonitor) {
        this.participantDataService = participantDataService;
        this.runtimeService = runtimeService;
        this.boundParticipantsMonitor = boundParticipantsMonitor;
    }

    public void registerTask(String businessKey,
                             String participantId,
                             String targetParticipantId,
                             String executionId,
                             String activityId,
                             Double timer) {
        if (businessKey == null || participantId == null || targetParticipantId == null || executionId == null || activityId == null) {
            log.warn("[BindingRegistry] Cannot register binding task due to null values: businessKey={}, participantId={}, targetParticipantId={}, executionId={}, activityId={}",
                    businessKey, participantId, targetParticipantId, executionId, activityId);
            return;
        }

        log.info("[BindingRegistry] Registering task: BK={} | {} -> {} | exec={} | activity={} | timer={}",
                businessKey, participantId, targetParticipantId, executionId, activityId, timer != null ? timer : "(empty)");

        BindingTaskInfo taskInfo = new BindingTaskInfo(
                businessKey,
                participantId,
                targetParticipantId,
                executionId,
                activityId,
                timer
        );
        waitingByParticipant.put(participantId, taskInfo);
        bindingTimerStartByExecutionId.remove(executionId);

        log.info("[BindingRegistry] Registered waiting binding task | BK: {} | participant: {} -> target: {}",
                businessKey, participantId, targetParticipantId);

        log.info("[BindingRegistry] Waiting tasks after registration: {}", waitingByParticipant.entrySet().stream()
                .map(e -> e.getKey() + "=>" + e.getValue().targetParticipantId())
                .toList());

        tryActivatePair(taskInfo);
    }

    public void removeTask(String businessKey, String participantId) {
        if (businessKey == null || participantId == null) {
            return;
        }

        BindingTaskInfo removedWaiting = waitingByParticipant.remove(participantId);
        if (removedWaiting != null) {
            bindingTimerStartByExecutionId.remove(removedWaiting.executionId());
        }

        List<String> pairKeysToRemove = new ArrayList<>();
        for (Map.Entry<String, BindingPair> entry : activePairs.entrySet()) {
            BindingPair pair = entry.getValue();
            boolean sameBusinessKey = businessKey.equals(pair.first.businessKey()) && businessKey.equals(pair.second.businessKey());
            boolean participantInPair = participantId.equals(pair.first.participantId()) || participantId.equals(pair.second.participantId());
            if (sameBusinessKey && participantInPair) {
                pairKeysToRemove.add(entry.getKey());
            }
        }

        for (String pairKey : pairKeysToRemove) {
            BindingPair removed = activePairs.remove(pairKey);
            if (removed != null) {
                cleanupPair(removed);
                log.info("[BindingRegistry] Removed active pair {} due to task end", pairKey);
            }
        }
    }

    /**
     * Returns pending binding notifications for a participant in a shape compatible
     * with the mobile pending-actions endpoint.
     */
    public List<Map<String, String>> getPendingBindingsForParticipant(String participantId) {
        if (participantId == null || participantId.isBlank()) {
            return List.of();
        }

        BindingTaskInfo waitingTask = waitingByParticipant.get(participantId);
        if (waitingTask != null) {
            return List.of(toBindingView(waitingTask, waitingTask.targetParticipantId()));
        }

        for (BindingPair pair : activePairs.values()) {
            if (participantId.equals(pair.first.participantId())) {
                if (!isPairAlreadyAtSamePosition(pair)) {
                    return List.of(toBindingView(pair.first, pair.second.participantId()));
                }
                return List.of();
            }
            if (participantId.equals(pair.second.participantId())) {
                if (!isPairAlreadyAtSamePosition(pair)) {
                    return List.of(toBindingView(pair.second, pair.first.participantId()));
                }
                return List.of();
            }
        }

        return List.of();
    }

    public boolean isBindingInProgressForParticipant(String participantId) {
        if (participantId == null || participantId.isBlank()) {
            return false;
        }

        if (waitingByParticipant.containsKey(participantId)) {
            return true;
        }

        for (BindingPair pair : activePairs.values()) {
            if (participantId.equals(pair.first.participantId()) || participantId.equals(pair.second.participantId())) {
                return true;
            }
        }

        return false;
    }

    private Map<String, String> toBindingView(BindingTaskInfo taskInfo, String counterpartParticipantId) {
        Map<String, String> view = new LinkedHashMap<>();
        view.put("executionId", taskInfo.executionId());
        view.put("action", "binding");
        view.put("counterpartParticipantId", counterpartParticipantId);
        view.put("message", "Move to same place as " + counterpartParticipantId + " (binding)");
        return view;
    }

    private boolean isPairAlreadyAtSamePosition(BindingPair pair) {
        String positionA = getParticipantPosition(pair.first.participantId());
        String positionB = getParticipantPosition(pair.second.participantId());
        return positionA != null && positionA.equals(positionB);
    }

    @Scheduled(fixedRate = 2000)
    public void checkBindingCompletion() {
        List<String> timedOutWaitingParticipants = new ArrayList<>();

        if (!waitingByParticipant.isEmpty()) {
            log.info("[BindingRegistry] Waiting for counterpart: {} participants in waiting | waiting={}",
                    waitingByParticipant.size(),
                    waitingByParticipant.entrySet().stream()
                            .map(e -> e.getKey() + "->" + e.getValue().targetParticipantId())
                            .toList());

            for (BindingTaskInfo waitingTask : waitingByParticipant.values()) {
                if (isBindingTimerExpired(waitingTask) && signalBindingTimeout(waitingTask)) {
                    timedOutWaitingParticipants.add(waitingTask.participantId());
                }
            }
        }

        for (String participantId : timedOutWaitingParticipants) {
            BindingTaskInfo removed = waitingByParticipant.remove(participantId);
            if (removed != null) {
                bindingTimerStartByExecutionId.remove(removed.executionId());
            }
        }

        if (activePairs.isEmpty()) {
            return;
        }

        List<String> pairsToRemove = new ArrayList<>();
        List<BindingTaskInfo> timedOutInPairs = new ArrayList<>();

        for (Map.Entry<String, BindingPair> entry : activePairs.entrySet()) {
            String pairKey = entry.getKey();
            BindingPair pair = entry.getValue();

            boolean firstTimedOut = isBindingTimerExpired(pair.first);
            boolean secondTimedOut = isBindingTimerExpired(pair.second);
            if (firstTimedOut || secondTimedOut) {
                if (firstTimedOut) {
                    timedOutInPairs.add(pair.first);
                }
                if (secondTimedOut) {
                    timedOutInPairs.add(pair.second);
                }
                pairsToRemove.add(pairKey);
                continue;
            }

            String positionA = getParticipantPosition(pair.first.participantId());
            String positionB = getParticipantPosition(pair.second.participantId());

            log.info("[BindingRegistry] Checking pair {}: {} at {} vs {} at {}",
                    pairKey,
                    pair.first.participantId(), positionA,
                    pair.second.participantId(), positionB);

            if (positionA == null || positionB == null) {
                continue;
            }

            if (positionA.equals(positionB)) {
                log.info("[BindingRegistry] Pair {} reached same position '{}' -> completing binding tasks",
                        pairKey, positionA);
                try {
                    String processInstanceId = getProcessInstanceId(pair.first.executionId());

                    runtimeService.signal(pair.first.executionId());
                    runtimeService.signal(pair.second.executionId());
                    
                    // Notify the discordance monitor that this pair is now bound
                    boundParticipantsMonitor.markBound(
                            pair.first.businessKey(),
                        processInstanceId,
                            pair.first.participantId(),
                            pair.second.participantId()
                    );
                    
                    pairsToRemove.add(pairKey);
                } catch (Exception e) {
                    log.error("[BindingRegistry] Failed to signal pair {}: {}", pairKey, e.getMessage(), e);
                }
            }
        }

        Set<String> uniquePairKeys = ConcurrentHashMap.newKeySet();
        uniquePairKeys.addAll(pairsToRemove);
        for (String pairKey : uniquePairKeys) {
            BindingPair removed = activePairs.remove(pairKey);
            if (removed != null) {
                cleanupPair(removed);
                log.info("[BindingRegistry] Completed/removed pair {}", pairKey);
            }
        }

        for (BindingTaskInfo timedOutTask : timedOutInPairs) {
            signalBindingTimeout(timedOutTask);
        }
    }

    private boolean signalBindingTimeout(BindingTaskInfo taskInfo) {
        if (!isExecutionStillActive(taskInfo.executionId())) {
            return false;
        }

        try {
            runtimeService.setVariableLocal(taskInfo.executionId(), BPMN_ERROR_CODE_VAR, FAILED_BINDING_ERROR_CODE);
            runtimeService.setVariableLocal(
                    taskInfo.executionId(),
                    BPMN_ERROR_MESSAGE_VAR,
                    String.format(
                            "Binding with participant '%s' did not complete within timer for activity '%s'",
                            taskInfo.targetParticipantId(),
                            taskInfo.activityId()
                    )
            );
            runtimeService.signal(taskInfo.executionId());
            bindingTimerStartByExecutionId.remove(taskInfo.executionId());
            log.warn("[BindingRegistry] Binding timer expired -> raised '{}' | participant={} | target={} | activity={} | execution={} | timer={}s",
                    FAILED_BINDING_ERROR_CODE,
                    taskInfo.participantId(),
                    taskInfo.targetParticipantId(),
                    taskInfo.activityId(),
                    taskInfo.executionId(),
                    taskInfo.timer());
            return true;
        } catch (Exception e) {
            log.error("[BindingRegistry] Failed to signal timeout for execution {}: {}",
                    taskInfo.executionId(), e.getMessage(), e);
            return false;
        }
    }

    private boolean isBindingTimerExpired(BindingTaskInfo taskInfo) {
        Double timerSeconds = taskInfo.timer();
        if (timerSeconds == null || timerSeconds <= 0) {
            bindingTimerStartByExecutionId.remove(taskInfo.executionId());
            return false;
        }

        long startTime = bindingTimerStartByExecutionId.computeIfAbsent(taskInfo.executionId(), ignored -> {
            long now = System.currentTimeMillis();
            log.info("[BindingRegistry] Binding timer started | participant={} | target={} | activity={} | execution={} | timer={}s",
                    taskInfo.participantId(),
                    taskInfo.targetParticipantId(),
                    taskInfo.activityId(),
                    taskInfo.executionId(),
                    timerSeconds);
            return now;
        });

        long timeoutMillis = Math.round(timerSeconds * 1000.0d);
        long elapsed = System.currentTimeMillis() - startTime;

        if (elapsed >= timeoutMillis) {
            bindingTimerStartByExecutionId.remove(taskInfo.executionId());
            return true;
        }

        return false;
    }

    private boolean isExecutionStillActive(String executionId) {
        if (executionId == null || executionId.isBlank()) {
            return false;
        }
        try {
            return runtimeService.createExecutionQuery().executionId(executionId).singleResult() != null;
        } catch (Exception ex) {
            log.debug("[BindingRegistry] Failed to check execution {} activity state: {}", executionId, ex.getMessage());
            return false;
        }
    }

    private String getProcessInstanceId(String executionId) {
        if (executionId == null || executionId.isBlank()) {
            return null;
        }
        try {
            var execution = runtimeService.createExecutionQuery().executionId(executionId).singleResult();
            return execution != null ? execution.getProcessInstanceId() : null;
        } catch (Exception ex) {
            log.debug("[BindingRegistry] Failed to resolve processInstanceId for execution {}: {}", executionId, ex.getMessage());
            return null;
        }
    }

    private void cleanupPair(BindingPair pair) {
        waitingByParticipant.remove(pair.first.participantId());
        waitingByParticipant.remove(pair.second.participantId());
        bindingTimerStartByExecutionId.remove(pair.first.executionId());
        bindingTimerStartByExecutionId.remove(pair.second.executionId());
    }

    private void tryActivatePair(BindingTaskInfo current) {
        log.info("[BindingRegistry] tryActivatePair: Looking for counterpart for {} -> {}",
                current.participantId(), current.targetParticipantId());

        BindingTaskInfo counterpart = waitingByParticipant.get(current.targetParticipantId());
        if (counterpart == null) {
            log.info("[BindingRegistry] tryActivatePair: No counterpart found for {} (waiting set: {})",
                    current.targetParticipantId(), waitingByParticipant.keySet());
            return;
        }

        log.info("[BindingRegistry] tryActivatePair: Found potential counterpart {} -> {}",
                counterpart.participantId(), counterpart.targetParticipantId());

        boolean sameBusinessKey = current.businessKey().equals(counterpart.businessKey());
        log.info("[BindingRegistry] tryActivatePair: Same business key? {} vs {} = {}",
                current.businessKey(), counterpart.businessKey(), sameBusinessKey);

        boolean participantMatch = current.participantId().equals(counterpart.targetParticipantId());
        boolean targetMatch = current.targetParticipantId().equals(counterpart.participantId());
        boolean reciprocalPair = participantMatch && targetMatch;

        log.info("[BindingRegistry] tryActivatePair: Reciprocal check - current.id ({}) == counterpart.target ({}) ? {} | current.target ({}) == counterpart.id ({}) ? {}",
                current.participantId(), counterpart.targetParticipantId(), participantMatch,
                current.targetParticipantId(), counterpart.participantId(), targetMatch);

        if (!sameBusinessKey || !reciprocalPair) {
            log.info("[BindingRegistry] tryActivatePair: Pair rejected - sameBusinessKey={}, reciprocalPair={}",
                    sameBusinessKey, reciprocalPair);
            return;
        }

        String pairKey = buildPairKey(current.businessKey(), current.participantId(), current.targetParticipantId());
        activePairs.put(pairKey, new BindingPair(current, counterpart));

        // Remove from waiting set once pair is active
        waitingByParticipant.remove(current.participantId());
        waitingByParticipant.remove(counterpart.participantId());

        log.info("[BindingRegistry] ✓ Activated pair {} ({} <-> {})",
                pairKey, current.participantId(), current.targetParticipantId());
    }

    private String getParticipantPosition(String participantId) {
        Optional<org.unicam.intermediate.models.pojo.Participant> participant =
                participantDataService.getParticipant(participantId);
        return participant.map(org.unicam.intermediate.models.pojo.Participant::getPosition).orElse(null);
    }

    private String buildPairKey(String businessKey, String participantA, String participantB) {
        if (participantA.compareTo(participantB) <= 0) {
            return businessKey + ":" + participantA + "<->" + participantB;
        }
        return businessKey + ":" + participantB + "<->" + participantA;
    }

    private record BindingPair(BindingTaskInfo first, BindingTaskInfo second) {
    }
}
