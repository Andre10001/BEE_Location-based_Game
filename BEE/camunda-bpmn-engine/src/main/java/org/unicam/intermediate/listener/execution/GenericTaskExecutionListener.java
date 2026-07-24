package org.unicam.intermediate.listener.execution;

import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.ExecutionListener;
import org.camunda.bpm.model.bpmn.instance.ExtensionElements;
import org.camunda.bpm.model.bpmn.instance.Task;
import org.camunda.bpm.model.xml.instance.DomElement;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.springframework.stereotype.Component;
import org.unicam.intermediate.service.environmental.EnvironmentalTaskRegistry;
import org.unicam.intermediate.service.participant.ParticipantService;

import static org.unicam.intermediate.utils.Constants.SPACE_NS;
import static org.unicam.intermediate.utils.Constants.genericTaskExecutionListenerBeanName;

@Slf4j
@Component(genericTaskExecutionListenerBeanName)
public class GenericTaskExecutionListener implements ExecutionListener {

    private static final String GUARD_LOCAL_NAME = "guard";
    private static final String BPMN_ERROR_CODE_VAR = "__spaceBpmnErrorCode";
    private static final String BPMN_ERROR_MESSAGE_VAR = "__spaceBpmnErrorMessage";

    private final EnvironmentalTaskRegistry environmentalTaskRegistry;
    private final ParticipantService participantService;

    public GenericTaskExecutionListener(EnvironmentalTaskRegistry environmentalTaskRegistry,
                                        ParticipantService participantService) {
        this.environmentalTaskRegistry = environmentalTaskRegistry;
        this.participantService = participantService;
    }

    @Override
    public void notify(DelegateExecution execution) {
        if (EVENTNAME_START.equals(execution.getEventName())) {
            handleStart(execution);
        } else if (EVENTNAME_END.equals(execution.getEventName())) {
            handleEnd(execution);
        }
    }

    private void handleStart(DelegateExecution execution) {
        String guardValue = extractGuardValue(execution);
        if (guardValue == null || guardValue.isBlank()) {
            return;
        }

        org.unicam.intermediate.models.Participant participant = participantService.resolveCurrentParticipant(execution);
        String participantId = participant != null ? participant.getId() : null;

        // Reuse environmental registry: guard only, no action and no timer.
        environmentalTaskRegistry.registerTask(
                execution.getId(),
                execution.getCurrentActivityId(),
                guardValue,
                null,
                participantId,
                null
        );

        log.info("[GENERIC_TASK] WAITING | Activity: {} - {} | Guard: {}",
                execution.getCurrentActivityId(),
                execution.getCurrentActivityName() != null ? execution.getCurrentActivityName() : "(unnamed)",
                guardValue);
    }

    private void handleEnd(DelegateExecution execution) {
        String bpmnErrorCode = (String) execution.getVariableLocal(BPMN_ERROR_CODE_VAR);
        String bpmnErrorMessage = (String) execution.getVariableLocal(BPMN_ERROR_MESSAGE_VAR);
        execution.removeVariableLocal(BPMN_ERROR_CODE_VAR);
        execution.removeVariableLocal(BPMN_ERROR_MESSAGE_VAR);

        environmentalTaskRegistry.removeTask(execution.getId(), execution.getCurrentActivityId());

        if (bpmnErrorCode != null && !bpmnErrorCode.isBlank()) {
            throw new BpmnError(
                    bpmnErrorCode,
                    bpmnErrorMessage != null ? bpmnErrorMessage : "Task guard failed"
            );
        }
    }

    private String extractGuardValue(DelegateExecution execution) {
        return extractExtensionValue(execution, GUARD_LOCAL_NAME);
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
