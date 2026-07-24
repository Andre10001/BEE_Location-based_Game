package org.unicam.intermediate.service.environmental.unbinding;

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
public class UnbindingTaskRegistry {

    private static final String BPMN_ERROR_CODE_VAR = "__spaceBpmnErrorCode";
    private static final String BPMN_ERROR_MESSAGE_VAR = "__spaceBpmnErrorMessage";
    private static final String FAILED_UNBINDING_ERROR_CODE = "failedUnbinding";

    private final ParticipantDataService participantDataService;
    private final RuntimeService runtimeService;
    private final BoundParticipantsDiscordanceMonitor boundParticipantsMonitor;

    // participantId -> unbinding task currently waiting for counterpart
    private final Map<String, BindingTaskInfo> waitingByParticipant = new ConcurrentHashMap<>();

    // pairKey -> active paired unbinding tasks to monitor by position
    private final Map<String, UnbindingPair> activePairs = new ConcurrentHashMap<>();

    // executionId -> epoch millis when unbinding timer started
    private final Map<String, Long> unbindingTimerStartByExecutionId = new ConcurrentHashMap<>();

    public UnbindingTaskRegistry(ParticipantDataService participantDataService,
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
            log.warn("[UnbindingRegistry] Cannot register unbinding task due to null values: businessKey={}, participantId={}, targetParticipantId={}, executionId={}, activityId={}",
                    businessKey, participantId, targetParticipantId, executionId, activityId);
            return;
        }

        log.info("[UnbindingRegistry] Registering task: BK={} | {} -> {} | exec={} | activity={} | timer={}",
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
        unbindingTimerStartByExecutionId.remove(executionId);

        log.info("[UnbindingRegistry] Registered waiting unbinding task | BK: {} | participant: {} -> target: {}",
                businessKey, participantId, targetParticipantId);

        tryActivatePair(taskInfo);
    }

    public void removeTask(String businessKey, String participantId) {
        if (businessKey == null || participantId == null) {
            return;
        }

        BindingTaskInfo removedWaiting = waitingByParticipant.remove(participantId);
        if (removedWaiting != null) {
            unbindingTimerStartByExecutionId.remove(removedWaiting.executionId());
        }

        List<String> pairKeysToRemove = new ArrayList<>();
        for (Map.Entry<String, UnbindingPair> entry : activePairs.entrySet()) {
            UnbindingPair pair = entry.getValue();
            boolean sameBusinessKey = businessKey.equals(pair.first.businessKey()) && businessKey.equals(pair.second.businessKey());
            boolean participantInPair = participantId.equals(pair.first.participantId()) || participantId.equals(pair.second.participantId());
            if (sameBusinessKey && participantInPair) {
                pairKeysToRemove.add(entry.getKey());
            }
        }

        for (String pairKey : pairKeysToRemove) {
            UnbindingPair removed = activePairs.remove(pairKey);
            if (removed != null) {
                cleanupPair(removed);
                log.info("[UnbindingRegistry] Removed active pair {} due to task end", pairKey);
            }
        }
    }

    /**
     * Returns pending unbinding notifications for a participant in a shape compatible
     * with the mobile pending-actions endpoint.
     */
    public List<Map<String, String>> getPendingUnbindingsForParticipant(String participantId) {
        if (participantId == null || participantId.isBlank()) {
            return List.of();
        }

        BindingTaskInfo waitingTask = waitingByParticipant.get(participantId);
        if (waitingTask != null) {
            return List.of(toUnbindingView(waitingTask, waitingTask.targetParticipantId()));
        }

        for (UnbindingPair pair : activePairs.values()) {
            if (participantId.equals(pair.first.participantId())) {
                if (!isPairAlreadyAtSamePosition(pair)) {
                    return List.of(toUnbindingView(pair.first, pair.second.participantId()));
                }
                return List.of();
            }
            if (participantId.equals(pair.second.participantId())) {
                if (!isPairAlreadyAtSamePosition(pair)) {
                    return List.of(toUnbindingView(pair.second, pair.first.participantId()));
                }
                return List.of();
            }
        }

        return List.of();
    }

    private Map<String, String> toUnbindingView(BindingTaskInfo taskInfo, String counterpartParticipantId) {
        Map<String, String> view = new LinkedHashMap<>();
        view.put("executionId", taskInfo.executionId());
        view.put("action", "unbinding");
        view.put("counterpartParticipantId", counterpartParticipantId);
        view.put("message", "Move to same place as " + counterpartParticipantId + " (unbinding)");
        return view;
    }

    private boolean isPairAlreadyAtSamePosition(UnbindingPair pair) {
        String positionA = getParticipantPosition(pair.first.participantId());
        String positionB = getParticipantPosition(pair.second.participantId());
        return positionA != null && positionA.equals(positionB);
    }

    @Scheduled(fixedRate = 2000)
    public void checkUnbindingCompletion() {
        List<String> timedOutWaitingParticipants = new ArrayList<>();

        if (!waitingByParticipant.isEmpty()) {
            for (BindingTaskInfo waitingTask : waitingByParticipant.values()) {
                if (isUnbindingTimerExpired(waitingTask) && signalUnbindingTimeout(waitingTask)) {
                    timedOutWaitingParticipants.add(waitingTask.participantId());
                }
            }
        }

        for (String participantId : timedOutWaitingParticipants) {
            BindingTaskInfo removed = waitingByParticipant.remove(participantId);
            if (removed != null) {
                unbindingTimerStartByExecutionId.remove(removed.executionId());
            }
        }

        if (activePairs.isEmpty()) {
            return;
        }

        List<String> pairsToRemove = new ArrayList<>();
        List<BindingTaskInfo> timedOutInPairs = new ArrayList<>();

        for (Map.Entry<String, UnbindingPair> entry : activePairs.entrySet()) {
            String pairKey = entry.getKey();
            UnbindingPair pair = entry.getValue();

            boolean firstTimedOut = isUnbindingTimerExpired(pair.first);
            boolean secondTimedOut = isUnbindingTimerExpired(pair.second);
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

            log.debug("[UnbindingRegistry] Checking pair {}: {} at {} vs {} at {}",
                    pairKey,
                    pair.first.participantId(), positionA,
                    pair.second.participantId(), positionB);

            if (positionA == null || positionB == null) {
                continue;
            }

            if (positionA.equals(positionB)) {
                log.info("[UnbindingRegistry] Pair {} reached same position '{}' -> completing unbinding tasks",
                        pairKey, positionA);
                try {
                    String processInstanceId = getProcessInstanceId(pair.first.executionId());

                    runtimeService.signal(pair.first.executionId());
                    runtimeService.signal(pair.second.executionId());
                    
                    // Notify the discordance monitor that this pair is no longer bound
                    boundParticipantsMonitor.markUnbound(
                            pair.first.businessKey(),
                        processInstanceId,
                            pair.first.participantId(),
                            pair.second.participantId()
                    );
                    
                    pairsToRemove.add(pairKey);
                } catch (Exception e) {
                    log.error("[UnbindingRegistry] Failed to signal pair {}: {}", pairKey, e.getMessage(), e);
                }
            }
        }

        Set<String> uniquePairKeys = ConcurrentHashMap.newKeySet();
        uniquePairKeys.addAll(pairsToRemove);
        for (String pairKey : uniquePairKeys) {
            UnbindingPair removed = activePairs.remove(pairKey);
            if (removed != null) {
                cleanupPair(removed);
                log.info("[UnbindingRegistry] Completed/removed pair {}", pairKey);
            }
        }

        for (BindingTaskInfo timedOutTask : timedOutInPairs) {
            signalUnbindingTimeout(timedOutTask);
        }
    }

    private boolean signalUnbindingTimeout(BindingTaskInfo taskInfo) {
        if (!isExecutionStillActive(taskInfo.executionId())) {
            return false;
        }

        try {
            runtimeService.setVariableLocal(taskInfo.executionId(), BPMN_ERROR_CODE_VAR, FAILED_UNBINDING_ERROR_CODE);
            runtimeService.setVariableLocal(
                    taskInfo.executionId(),
                    BPMN_ERROR_MESSAGE_VAR,
                    String.format(
                            "Unbinding with participant '%s' did not complete within timer for activity '%s'",
                            taskInfo.targetParticipantId(),
                            taskInfo.activityId()
                    )
            );
            runtimeService.signal(taskInfo.executionId());
            unbindingTimerStartByExecutionId.remove(taskInfo.executionId());
            log.warn("[UnbindingRegistry] Unbinding timer expired -> raised '{}' | participant={} | target={} | activity={} | execution={} | timer={}s",
                    FAILED_UNBINDING_ERROR_CODE,
                    taskInfo.participantId(),
                    taskInfo.targetParticipantId(),
                    taskInfo.activityId(),
                    taskInfo.executionId(),
                    taskInfo.timer());
            return true;
        } catch (Exception e) {
            log.error("[UnbindingRegistry] Failed to signal timeout for execution {}: {}",
                    taskInfo.executionId(), e.getMessage(), e);
            return false;
        }
    }

    private boolean isUnbindingTimerExpired(BindingTaskInfo taskInfo) {
        Double timerSeconds = taskInfo.timer();
        if (timerSeconds == null || timerSeconds <= 0) {
            unbindingTimerStartByExecutionId.remove(taskInfo.executionId());
            return false;
        }

        long startTime = unbindingTimerStartByExecutionId.computeIfAbsent(taskInfo.executionId(), ignored -> {
            long now = System.currentTimeMillis();
            log.info("[UnbindingRegistry] Unbinding timer started | participant={} | target={} | activity={} | execution={} | timer={}s",
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
            unbindingTimerStartByExecutionId.remove(taskInfo.executionId());
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
            log.debug("[UnbindingRegistry] Failed to check execution {} activity state: {}", executionId, ex.getMessage());
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
            log.debug("[UnbindingRegistry] Failed to resolve processInstanceId for execution {}: {}", executionId, ex.getMessage());
            return null;
        }
    }

    private void cleanupPair(UnbindingPair pair) {
        waitingByParticipant.remove(pair.first.participantId());
        waitingByParticipant.remove(pair.second.participantId());
        unbindingTimerStartByExecutionId.remove(pair.first.executionId());
        unbindingTimerStartByExecutionId.remove(pair.second.executionId());
    }

    private void tryActivatePair(BindingTaskInfo current) {
        BindingTaskInfo counterpart = waitingByParticipant.get(current.targetParticipantId());
        if (counterpart == null) {
            return;
        }

        boolean sameBusinessKey = current.businessKey().equals(counterpart.businessKey());
        boolean reciprocalPair =
                current.participantId().equals(counterpart.targetParticipantId()) &&
                current.targetParticipantId().equals(counterpart.participantId());

        if (!sameBusinessKey || !reciprocalPair) {
            return;
        }

        String pairKey = buildPairKey(current.businessKey(), current.participantId(), current.targetParticipantId());
        activePairs.put(pairKey, new UnbindingPair(current, counterpart));

        // Remove from waiting set once pair is active
        waitingByParticipant.remove(current.participantId());
        waitingByParticipant.remove(counterpart.participantId());

        log.info("[UnbindingRegistry] Activated pair {} ({} <-> {})",
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

    private record UnbindingPair(BindingTaskInfo first, BindingTaskInfo second) {
    }
}
