package org.unicam.intermediate.listener.execution;

import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.ExecutionListener;
import org.camunda.bpm.model.bpmn.instance.ExclusiveGateway;
import org.camunda.bpm.model.bpmn.instance.ExtensionElements;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.bpm.model.xml.instance.DomElement;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.springframework.stereotype.Component;
import org.unicam.intermediate.models.Participant;
import org.unicam.intermediate.service.ExclusiveGatewayGuardRegistry;
import org.unicam.intermediate.service.participant.ParticipantService;

import java.util.List;

import static org.unicam.intermediate.utils.Constants.SPACE_NS;
import static org.unicam.intermediate.utils.Constants.exclusiveGatewayExecutionListenerBeanName;

@Slf4j
@Component(exclusiveGatewayExecutionListenerBeanName)
public class ExclusiveGatewayExecutionListener implements ExecutionListener {

    private static final String GATEWAY_BYPASS_VAR = "__spaceGatewayBypass";
    private final ExclusiveGatewayGuardRegistry exclusiveGatewayGuardRegistry;
    private final ParticipantService participantService;

    public ExclusiveGatewayExecutionListener(ExclusiveGatewayGuardRegistry exclusiveGatewayGuardRegistry,
                                             ParticipantService participantService) {
        this.exclusiveGatewayGuardRegistry = exclusiveGatewayGuardRegistry;
        this.participantService = participantService;
    }

    @Override
    public void notify(DelegateExecution execution) {
        if (EVENTNAME_START.equals(execution.getEventName())) {
            onStart(execution);
        } else if (EVENTNAME_END.equals(execution.getEventName())) {
            exclusiveGatewayGuardRegistry.removeGateway(execution.getId(), execution.getCurrentActivityId());
        }
    }

    private void onStart(DelegateExecution execution) {
        ModelElementInstance modelElement = execution.getBpmnModelElementInstance();
        if (!(modelElement instanceof ExclusiveGateway gateway)) {
            execution.setVariableLocal(GATEWAY_BYPASS_VAR, true);
            return;
        }

        List<String> guardedOutgoing = gateway.getOutgoing().stream()
                .filter(this::hasSpaceGuard)
                .map(SequenceFlow::getId)
                .toList();

        if (guardedOutgoing.isEmpty()) {
            execution.setVariableLocal(GATEWAY_BYPASS_VAR, true);
            log.debug("[ExclusiveGatewayListener] No guarded outgoing flows on gateway {} -> bypass wait",
                    execution.getCurrentActivityId());
            return;
        }

        execution.setVariableLocal(GATEWAY_BYPASS_VAR, false);
        Participant participant = participantService.resolveCurrentParticipant(execution);
        String participantId = participant != null ? participant.getId() : null;

        exclusiveGatewayGuardRegistry.registerGateway(
                execution.getId(),
                execution.getCurrentActivityId(),
                execution.getProcessDefinitionId(),
            participantId,
                guardedOutgoing
        );

        log.info("[ExclusiveGatewayListener] Gateway {} entered wait mode with {} guarded flows",
                execution.getCurrentActivityId(),
                guardedOutgoing.size());
    }

    private boolean hasSpaceGuard(SequenceFlow sequenceFlow) {
        ExtensionElements extensionElements = sequenceFlow.getExtensionElements();
        if (extensionElements == null || extensionElements.getDomElement() == null) {
            return false;
        }

        return extensionElements.getDomElement().getChildElements().stream()
                .filter(domElement -> isSpaceGuard(domElement))
                .map(DomElement::getTextContent)
                .map(String::trim)
                .anyMatch(text -> !text.isBlank());
    }

    private boolean isSpaceGuard(DomElement domElement) {
        return "guard".equalsIgnoreCase(domElement.getLocalName())
                && SPACE_NS.getNamespaceUri().equals(domElement.getNamespaceURI());
    }
}
