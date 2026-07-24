package org.unicam.intermediate.activity;

import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.impl.bpmn.behavior.AbstractBpmnActivityBehavior;
import org.camunda.bpm.engine.impl.pvm.delegate.ActivityBehavior;
import org.camunda.bpm.engine.impl.pvm.delegate.ActivityExecution;
import org.camunda.bpm.model.bpmn.instance.ExtensionElements;
import org.camunda.bpm.model.bpmn.instance.SendTask;
import org.camunda.bpm.model.xml.instance.DomElement;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.unicam.intermediate.service.environmental.EnvironmentalTaskRegistry;
import org.unicam.intermediate.service.participant.ParticipantService;

import static org.unicam.intermediate.utils.Constants.SPACE_NS;

@Slf4j
public class GuardedSendTaskActivity extends AbstractBpmnActivityBehavior {

    private static final String GUARD_LOCAL_NAME = "guard";
    private final ActivityBehavior delegateBehavior;
    private final EnvironmentalTaskRegistry environmentalTaskRegistry;
    private final ParticipantService participantService;

    public GuardedSendTaskActivity(ActivityBehavior delegateBehavior,
                                   EnvironmentalTaskRegistry environmentalTaskRegistry,
                                   ParticipantService participantService) {
        this.delegateBehavior = delegateBehavior;
        this.environmentalTaskRegistry = environmentalTaskRegistry;
        this.participantService = participantService;
    }

    @Override
    public void execute(ActivityExecution execution) throws Exception {
        String guardValue = extractGuardValue(execution);
        if (guardValue == null || guardValue.isBlank()) {
            delegateBehavior.execute(execution);
            return;
        }

        org.unicam.intermediate.models.Participant participant = participantService.resolveCurrentParticipant(execution);
        String participantId = participant != null ? participant.getId() : null;

        environmentalTaskRegistry.registerTask(
                execution.getId(),
                execution.getActivity().getId(),
                guardValue,
                null,
                participantId,
                null
        );

        log.info("[GUARDED_SEND_TASK] WAITING | Activity: {} - {} | Guard: {}",
                execution.getActivity().getId(),
                execution.getActivity().getProperty("name") != null ? execution.getActivity().getProperty("name") : "(unnamed)",
                guardValue);
    }

    @Override
    public void signal(ActivityExecution execution, String signalName, Object signalData) throws Exception {
        log.info("[GUARDED_SEND_TASK] Guard satisfied for activity '{}', executing send task delegate",
                execution.getActivity().getId());
        delegateBehavior.execute(execution);
    }

    private String extractGuardValue(ActivityExecution execution) {
        ModelElementInstance modelElement = execution.getBpmnModelElementInstance();
        if (!(modelElement instanceof SendTask sendTask)) {
            return null;
        }

        ExtensionElements extensionElements = sendTask.getExtensionElements();
        if (extensionElements == null) {
            return null;
        }

        return extensionElements.getDomElement().getChildElements().stream()
                .filter(domElement -> isSpaceElement(domElement, GUARD_LOCAL_NAME))
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