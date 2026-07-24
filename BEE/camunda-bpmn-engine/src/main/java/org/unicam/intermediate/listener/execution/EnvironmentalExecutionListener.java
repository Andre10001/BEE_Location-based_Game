package org.unicam.intermediate.listener.execution;

import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.ExecutionListener;
import org.camunda.bpm.model.bpmn.instance.ExtensionElements;
import org.camunda.bpm.model.bpmn.instance.Task;
import org.camunda.bpm.model.xml.instance.DomElement;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.unicam.intermediate.service.environmental.EnvironmentalTaskRegistry;
import org.unicam.intermediate.service.participant.ParticipantService;
import org.springframework.stereotype.Component;

import static org.unicam.intermediate.utils.Constants.SPACE_NS;
import static org.unicam.intermediate.utils.Constants.environmentalExecutionListenerBeanName;

@Slf4j
@Component(environmentalExecutionListenerBeanName)
public class EnvironmentalExecutionListener implements ExecutionListener {

    private static final String GUARD_LOCAL_NAME = "guard";
    private static final String ACTION_LOCAL_NAME = "action";
    private static final String TIMER_LOCAL_NAME = "timer";
    private static final String BPMN_ERROR_CODE_VAR = "__spaceBpmnErrorCode";
    private static final String BPMN_ERROR_MESSAGE_VAR = "__spaceBpmnErrorMessage";
    private final EnvironmentalTaskRegistry environmentalTaskRegistry;
    private final ParticipantService participantService;

    public EnvironmentalExecutionListener(EnvironmentalTaskRegistry environmentalTaskRegistry,
                                          ParticipantService participantService) {
        this.environmentalTaskRegistry = environmentalTaskRegistry;
        this.participantService = participantService;
    }

    @Override
    public void notify(DelegateExecution execution) {
        if (EVENTNAME_START.equals(execution.getEventName())) {
            handleEnvironmentalStart(execution);
        } else if (EVENTNAME_END.equals(execution.getEventName())) {
            handleEnvironmentalEnd(execution);
        }
    }

    private void handleEnvironmentalStart(DelegateExecution execution) {
        String guardValue = extractGuardValue(execution);
        String actionValue = extractActionValue(execution);
        Double timerValue = extractTimerValue(execution);

        org.unicam.intermediate.models.Participant participant = participantService.resolveCurrentParticipant(execution);
        String participantId = participant != null ? participant.getId() : null;

        environmentalTaskRegistry.registerTask(
            execution.getId(),
            execution.getCurrentActivityId(),
            guardValue,
            actionValue,
            participantId,
            timerValue
        );

        log.info("[ENVIRONMENTAL] WAITING | Activity: {} - {} | Guard: {} | Action: {} | Timer: {}",
                execution.getCurrentActivityId(),
                execution.getCurrentActivityName() != null ? execution.getCurrentActivityName() : "(unnamed)",
            guardValue != null && !guardValue.isBlank() ? guardValue : "(empty)",
            actionValue != null && !actionValue.isBlank() ? actionValue : "(empty)",
            timerValue != null ? timerValue : "(empty)");
    }

    private void handleEnvironmentalEnd(DelegateExecution execution) {
        String bpmnErrorCode = (String) execution.getVariableLocal(BPMN_ERROR_CODE_VAR);
        String bpmnErrorMessage = (String) execution.getVariableLocal(BPMN_ERROR_MESSAGE_VAR);
        execution.removeVariableLocal(BPMN_ERROR_CODE_VAR);
        execution.removeVariableLocal(BPMN_ERROR_MESSAGE_VAR);

        environmentalTaskRegistry.removeTask(execution.getId(), execution.getCurrentActivityId());
        log.info("[ENVIRONMENTAL] Task {} ended", execution.getCurrentActivityId());

        if (bpmnErrorCode != null && !bpmnErrorCode.isBlank()) {
            throw new BpmnError(bpmnErrorCode, bpmnErrorMessage != null ? bpmnErrorMessage : "Environmental task failed");
        }
    }

    private String extractGuardValue(DelegateExecution execution) {
        return extractExtensionValue(execution, GUARD_LOCAL_NAME);
    }

    private String extractActionValue(DelegateExecution execution) {
        return extractExtensionValue(execution, ACTION_LOCAL_NAME);
    }

    private Double extractTimerValue(DelegateExecution execution) {
        String raw = extractExtensionValue(execution, TIMER_LOCAL_NAME);
        if (raw == null || raw.isBlank()) {
            return null;
        }

        try {
            double parsed = Double.parseDouble(raw.trim());
            if (parsed < 0) {
                log.warn("[ENVIRONMENTAL] Invalid negative timer '{}' for activity {}. Ignoring.",
                        raw, execution.getCurrentActivityId());
                return null;
            }
            return parsed;
        } catch (NumberFormatException ex) {
            log.warn("[ENVIRONMENTAL] Invalid non-numeric timer '{}' for activity {}. Ignoring.",
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
