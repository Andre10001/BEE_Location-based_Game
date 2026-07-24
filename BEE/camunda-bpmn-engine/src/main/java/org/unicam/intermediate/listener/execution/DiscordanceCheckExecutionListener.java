package org.unicam.intermediate.listener.execution;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.ExecutionListener;
import org.springframework.stereotype.Component;
import org.unicam.intermediate.service.environmental.BoundParticipantsDiscordanceMonitor;

import java.util.List;
import java.util.Map;

/**
 * Global execution listener that checks for discordant positions at task start.
 * 
 * This listener is invoked when ANY task starts in the process.
 * It checks if there are any bound participant pairs with discordant positions
 * and raises a BpmnError if so, allowing event sub-processes to catch it.
 */
@Slf4j
@Component("discordanceCheckExecutionListener")
@AllArgsConstructor
public class DiscordanceCheckExecutionListener implements ExecutionListener {

    private final BoundParticipantsDiscordanceMonitor boundParticipantsMonitor;

    @Override
    public void notify(DelegateExecution execution) {
        // Only check at task start
        if (!EVENTNAME_START.equals(execution.getEventName())) {
            return;
        }

        String businessKey = execution.getBusinessKey();
        if (businessKey == null || businessKey.isBlank()) {
            return;
        }
        String processInstanceId = execution.getProcessInstanceId();

        // Get all monitored pairs for this process instance
        List<Map<String, Object>> monitoredPairs = boundParticipantsMonitor.getMonitoredPairs();
        
        for (Map<String, Object> pairInfo : monitoredPairs) {
            String pairBusinessKey = (String) pairInfo.get("businessKey");
            String pairProcessInstanceId = (String) pairInfo.get("processInstanceId");

            // Prefer strict process-instance match when available
            if (pairProcessInstanceId != null && !pairProcessInstanceId.isBlank()) {
                if (!processInstanceId.equals(pairProcessInstanceId)) {
                    continue;
                }
            }
            
            // Check if this pair belongs to this process instance
            if (!businessKey.equals(pairBusinessKey)) {
                continue;
            }

            // Check if the pair has already raised an error (to avoid duplicate errors)
            Boolean errorRaised = (Boolean) pairInfo.get("errorRaised");
            if (errorRaised != null && errorRaised) {
                continue;
            }

            // Check if positions are discordant
            Boolean concordant = (Boolean) pairInfo.get("concordant");
            if (concordant != null && !concordant) {
                // Positions are discordant - raise error
                String participantA = (String) pairInfo.get("participantA");
                String participantB = (String) pairInfo.get("participantB");
                Long remainingGraceMs = (Long) pairInfo.get("remainingGraceMs");

                if (remainingGraceMs <= 0) {
                    // Grace window has expired
                    log.warn("[DiscordanceCheckListener] Discordant positions detected at task start! " +
                             "Task: {} | Process: {} | Participants: {} <-> {} | Raising BpmnError",
                            execution.getCurrentActivityId(), businessKey, participantA, participantB);

                    // Set variables in execution context
                    execution.setVariable("discordantPositions_participantA", participantA);
                    execution.setVariable("discordantPositions_participantB", participantB);

                    // Raise the error - this will be caught by event sub-process
                    throw new BpmnError("discordantPositions",
                            "Participants " + participantA + " and " + participantB + 
                            " are in discordant positions (different places)");
                }
            }
        }
    }
}
