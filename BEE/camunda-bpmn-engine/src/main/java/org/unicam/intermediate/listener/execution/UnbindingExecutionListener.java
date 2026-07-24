// src/main/java/org/unicam/intermediate/listener/execution/UnbindingExecutionListener.java

package org.unicam.intermediate.listener.execution;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.ExecutionListener;
import org.camunda.bpm.model.bpmn.instance.ExtensionElements;
import org.camunda.bpm.model.bpmn.instance.Task;
import org.camunda.bpm.model.xml.instance.DomElement;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.springframework.stereotype.Component;
import org.unicam.intermediate.models.Participant;
import org.unicam.intermediate.service.MessageFlowRegistry;
import org.unicam.intermediate.service.MessageFlowRegistry.MessageFlowBinding;
import org.unicam.intermediate.service.participant.ParticipantService;
import org.unicam.intermediate.service.participant.UserParticipantMappingService;
import org.unicam.intermediate.service.environmental.unbinding.UnbindingTaskRegistry;

import static org.unicam.intermediate.utils.Constants.SPACE_NS;
import static org.unicam.intermediate.utils.Constants.unbindingExecutionListenerBeanName;

@Slf4j
@Component(unbindingExecutionListenerBeanName)
@AllArgsConstructor
public class UnbindingExecutionListener implements ExecutionListener {

    private static final String TIMER_LOCAL_NAME = "timer";
    private static final String BPMN_ERROR_CODE_VAR = "__spaceBpmnErrorCode";
    private static final String BPMN_ERROR_MESSAGE_VAR = "__spaceBpmnErrorMessage";

    private final UnbindingTaskRegistry unbindingTaskRegistry;
    private final MessageFlowRegistry messageFlowRegistry;
    private final ParticipantService participantService;
    private final UserParticipantMappingService userParticipantMapping;

    @Override
    public void notify(DelegateExecution execution) {
        if (EVENTNAME_START.equals(execution.getEventName())) {
            handleUnbindingStart(execution);
        } else if (EVENTNAME_END.equals(execution.getEventName())) {
            handleUnbindingEnd(execution);
        }
    }

    private void handleUnbindingStart(DelegateExecution execution) {
        String processDefinitionId = execution.getProcessDefinitionId();
        String activityId = execution.getCurrentActivityId();
        String businessKey = execution.getBusinessKey();
        Double timerValue = extractTimerValue(execution);

        // Get message flow unbinding info
        MessageFlowBinding flowBinding = messageFlowRegistry.getFlowBinding(processDefinitionId, activityId);
        if (flowBinding == null) {
            log.error("[UNBINDING] No message flow found for task: {} in process: {}", activityId, processDefinitionId);
            return;
        }

        String targetParticipantRef = activityId.equals(flowBinding.getSourceTaskRef())
                ? flowBinding.getTargetParticipantRef()
                : flowBinding.getSourceParticipantRef();

        Participant currentParticipant = participantService.resolveCurrentParticipant(execution);
        Participant targetParticipant = participantService.resolveTargetParticipant(execution, targetParticipantRef);

        if (currentParticipant == null || targetParticipant == null) {
            log.error("[UNBINDING] Failed to resolve participants for task {}: current={}, target={}",
                activityId, currentParticipant, targetParticipant);
            return;
        }

        String currentParticipantId = currentParticipant.getId();
        String targetParticipantId = targetParticipant.getId();

        String userId = (String) execution.getVariable("userId");
        if (userId != null && currentParticipantId != null && businessKey != null) {
            userParticipantMapping.registerUserAsParticipant(
                businessKey,
                userId,
                currentParticipantId
            );

            log.info("[UNBINDING] Auto-registered user {} as participant {} for BK {}",
                userId, currentParticipantId, businessKey);
        }

        log.info("[UNBINDING] Task {} started - Participant {} ({}) waiting for unbind with {} ({}) | Timer: {}",
            activityId, currentParticipant.getLogDisplayName(), currentParticipantId,
            targetParticipant.getLogDisplayName(), targetParticipantId,
            timerValue != null ? timerValue : "(empty)");

        unbindingTaskRegistry.registerTask(
            businessKey,
            currentParticipantId,
            targetParticipantId,
            execution.getId(),
            activityId,
            timerValue
        );
    }

    private void handleUnbindingEnd(DelegateExecution execution) {
        String bpmnErrorCode = (String) execution.getVariableLocal(BPMN_ERROR_CODE_VAR);
        String bpmnErrorMessage = (String) execution.getVariableLocal(BPMN_ERROR_MESSAGE_VAR);
        execution.removeVariableLocal(BPMN_ERROR_CODE_VAR);
        execution.removeVariableLocal(BPMN_ERROR_MESSAGE_VAR);

        String activityId = execution.getCurrentActivityId();
        String businessKey = execution.getBusinessKey();
        String processDefinitionId = execution.getProcessDefinitionId();

        MessageFlowBinding flowBinding = messageFlowRegistry.getFlowBinding(processDefinitionId, activityId);

        if (flowBinding != null) {
            Participant participant = participantService.resolveCurrentParticipant(execution);
            if (participant != null) {
                unbindingTaskRegistry.removeTask(businessKey, participant.getId());
            } else {
                log.warn("[UNBINDING] Could not resolve participant for task end cleanup: {}", activityId);
            }
        }

        execution.removeVariable("unbindingCompleted_" + activityId);
        execution.removeVariable("unbindingWaitReason_" + activityId);
        execution.removeVariable("unbindingWaitingFor_" + activityId);
        execution.removeVariable("unbindingPlaceId");
        execution.removeVariable("unbindingPlaceName");
        execution.removeVariable("unbindingTimestamp");
        execution.removeVariable("boundParticipantId");

        log.info("[UNBINDING] Task {} ended", activityId);

        if (bpmnErrorCode != null && !bpmnErrorCode.isBlank()) {
            throw new BpmnError(
                    bpmnErrorCode,
                    bpmnErrorMessage != null ? bpmnErrorMessage : "Unbinding task failed"
            );
        }
    }

    private Double extractTimerValue(DelegateExecution execution) {
        String raw = extractExtensionValue(execution, TIMER_LOCAL_NAME);
        if (raw == null || raw.isBlank()) {
            return null;
        }

        try {
            double parsed = Double.parseDouble(raw.trim());
            if (parsed < 0) {
                log.warn("[UNBINDING] Invalid negative timer '{}' for activity {}. Ignoring.",
                        raw, execution.getCurrentActivityId());
                return null;
            }
            return parsed;
        } catch (NumberFormatException ex) {
            log.warn("[UNBINDING] Invalid non-numeric timer '{}' for activity {}. Ignoring.",
                    raw, execution.getCurrentActivityId());
            return null;
        }
    }

    private String extractExtensionValue(DelegateExecution execution, String localName) {
        ModelElementInstance modelElement = execution.getBpmnModelElementInstance();
        if (!(modelElement instanceof Task task)) {
            return null;
        }

        ExtensionElements extensionElements = task.getExtensionElements();
        if (extensionElements == null) {
            return null;
        }

        return extensionElements.getDomElement().getChildElements().stream()
                .filter(domElement -> isSpaceElement(domElement, localName))
                .map(DomElement::getTextContent)
                .map(String::trim)
                .findFirst()
                .orElse(null);
    }

    private boolean isSpaceElement(DomElement domElement, String localName) {
        return localName.equalsIgnoreCase(domElement.getLocalName())
                && SPACE_NS.getNamespaceUri().equals(domElement.getNamespaceURI());
    }
}