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
import org.unicam.intermediate.models.Participant;
import org.unicam.intermediate.models.enums.TaskType;
import org.unicam.intermediate.service.environmental.EnvironmentDataService;
import org.unicam.intermediate.service.environmental.movement.MovementTaskRegistry;
import org.unicam.intermediate.service.participant.ParticipantDataService;
import org.unicam.intermediate.service.participant.ParticipantService;
import org.unicam.intermediate.service.participant.UserParticipantMappingService;
import org.unicam.intermediate.service.xml.AbstractXmlService;
import org.unicam.intermediate.service.xml.XmlServiceDispatcher;

import java.util.List;
import java.util.Optional;

import static org.unicam.intermediate.utils.Constants.*;

@Slf4j
@Component(movementExecutionListenerBeanName)
public class MovementExecutionListener implements ExecutionListener {

    private static final String TIMER_LOCAL_NAME = "timer";
    private static final String BPMN_ERROR_CODE_VAR = "__spaceBpmnErrorCode";
    private static final String BPMN_ERROR_MESSAGE_VAR = "__spaceBpmnErrorMessage";
    private static final String PARTICIPANT_DESTINATION_PREFIX = "Participant.";
    private static final String PARTICIPANT_POSITION_SUFFIX = ".position";

    private final XmlServiceDispatcher dispatcher;
    private final ParticipantService participantService;
    private final ParticipantDataService participantDataService;
    private final EnvironmentDataService environmentDataService;
    private final UserParticipantMappingService userParticipantMapping;
    private final MovementTaskRegistry movementTaskRegistry;

    public MovementExecutionListener(XmlServiceDispatcher dispatcher,
                                     ParticipantService participantService,
                                     ParticipantDataService participantDataService,
                                     EnvironmentDataService environmentDataService,
                                     UserParticipantMappingService userParticipantMapping,
                                     MovementTaskRegistry movementTaskRegistry) {
        this.dispatcher = dispatcher;
        this.participantService = participantService;
        this.participantDataService = participantDataService;
        this.environmentDataService = environmentDataService;
        this.userParticipantMapping = userParticipantMapping;
        this.movementTaskRegistry = movementTaskRegistry;
    }

    @Override
    public void notify(DelegateExecution execution) {
        if (EVENTNAME_START.equals(execution.getEventName())) {
            handleMovementStart(execution);
        } else if (EVENTNAME_END.equals(execution.getEventName())) {
            handleMovementEnd(execution);
        }
    }

    private void handleMovementStart(DelegateExecution execution) {
        AbstractXmlService svc = dispatcher.get(SPACE_NS.getNamespaceUri(), TaskType.MOVEMENT);
        String raw = svc.extractRaw(execution);
        String value = raw != null && raw.startsWith("${") && raw.endsWith("}")
                ? String.valueOf(execution.getVariable(raw.substring(2, raw.length()-1).trim()))
                : raw;
        String destination = resolveDestinationValue(value, execution);
        Double timerValue = extractTimerValue(execution);

        Participant participant = participantService.resolveCurrentParticipant(execution);
        boolean destinationIsPhysicalPlace = destination != null && environmentDataService.getPhysicalPlace(destination).isPresent();
        boolean destinationIsLogicalPlace = destination != null && environmentDataService.resolveLogicalPlaceId(destination).isPresent();

        if (participant != null && destinationIsLogicalPlace) {
            List<String> matchingPhysicalPlaces = movementTaskRegistry
                .resolveMatchingPhysicalPlaceIdsForLogicalDestination(destination);

            if (matchingPhysicalPlaces.isEmpty()) {
            throw new BpmnError(
                "unreachableDestination",
                String.format("Logical destination '%s' has no matching physical places for participant '%s'",
                    destination, participant.getId())
            );
            }
        }

        if (participant != null && destinationIsPhysicalPlace) {
            String participantId = participant.getId();
            participantDataService.getParticipant(participantId).ifPresent(current -> {
                String currentPosition = current.getPosition();
                boolean reachable = environmentDataService.existsPathBetweenPhysicalPlaces(currentPosition, destination);
                if (!reachable) {
                    throw new BpmnError(
                            "unreachableDestination",
                            String.format("No path found from '%s' to '%s' for participant '%s'",
                                    currentPosition, destination, participantId)
                    );
                }
            });
        }

        svc.patchInstanceValue(execution, destination);
        var activityId = execution.getCurrentActivityId();
        String varKey = activityId + "." + svc.getLocalName();
        execution.setVariable(varKey, destination);

        String businessKey = execution.getBusinessKey();

        String userId = (String) execution.getVariable("userId");

        if (userId != null && participant != null && businessKey != null) {
            userParticipantMapping.registerUserAsParticipant(
                    businessKey,
                    userId,
                    participant.getId()
            );

            log.info("[MOVEMENT] Auto-registered user {} as participant {} for BK {}",
                    userId, participant.getId(), businessKey);
        }

        String activityName = execution.getCurrentActivityName();
        
        log.info("[MOVEMENT] WAITING | Activity: {} - {} | Participant: {} | Destination: {} | Timer: {}",
                execution.getCurrentActivityId(),
                activityName != null ? activityName : "(unnamed)",
                participant.toString(),
                destination != null ? destination : "unknown location",
                timerValue != null ? timerValue : "(empty)");

        // Registra il task nel registry per il controllo periodico
        if (participant != null && destination != null) {
            movementTaskRegistry.registerTask(
                    participant.getId(),
                    execution.getId(),
                    execution.getCurrentActivityId(),
                    destination,
                    timerValue
            );
        }
    }

    private void handleMovementEnd(DelegateExecution execution) {
        String bpmnErrorCode = (String) execution.getVariableLocal(BPMN_ERROR_CODE_VAR);
        String bpmnErrorMessage = (String) execution.getVariableLocal(BPMN_ERROR_MESSAGE_VAR);
        execution.removeVariableLocal(BPMN_ERROR_CODE_VAR);
        execution.removeVariableLocal(BPMN_ERROR_MESSAGE_VAR);

        AbstractXmlService svc = dispatcher.get(SPACE_NS.getNamespaceUri(), TaskType.MOVEMENT);
        String raw = svc.extractRaw(execution);
        svc.restoreInstanceValue(execution, raw);

        // Rimuovi il task dal registry quando termina
        Participant participant = participantService.resolveCurrentParticipant(execution);
        if (participant != null) {
            movementTaskRegistry.removeTask(participant.getId(), execution.getId());
            log.info("[MOVEMENT] Task completed for participant {} (execution={})",
                    participant.getId(), execution.getId());
        }

        if (bpmnErrorCode != null && !bpmnErrorCode.isBlank()) {
            throw new BpmnError(
                    bpmnErrorCode,
                    bpmnErrorMessage != null ? bpmnErrorMessage : "Movement task failed"
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
                log.warn("[MOVEMENT] Invalid negative timer '{}' for activity {}. Ignoring.",
                        raw, execution.getCurrentActivityId());
                return null;
            }
            return parsed;
        } catch (NumberFormatException ex) {
            log.warn("[MOVEMENT] Invalid non-numeric timer '{}' for activity {}. Ignoring.",
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

    private String resolveDestinationValue(String value, DelegateExecution execution) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.startsWith(PARTICIPANT_DESTINATION_PREFIX)
                && trimmed.length() > PARTICIPANT_DESTINATION_PREFIX.length()) {
            String participantRef = normalizeParticipantReference(
                trimmed.substring(PARTICIPANT_DESTINATION_PREFIX.length()).trim()
            );

            Optional<org.unicam.intermediate.models.pojo.Participant> participantOpt =
                    participantDataService.getParticipantByName(participantRef)
                            .or(() -> participantDataService.getParticipant(participantRef));

            if (participantOpt.isEmpty()) {
                throw new BpmnError(
                        "unreachableDestination",
                        String.format("Destination '%s' references unknown participant '%s'", trimmed, participantRef)
                );
            }

            String participantPosition = participantOpt.get().getPosition();
            if (participantPosition == null || participantPosition.isBlank()) {
                throw new BpmnError(
                        "unreachableDestination",
                        String.format("Destination '%s' references participant '%s' without a valid position", trimmed, participantRef)
                );
            }

            String resolved = environmentDataService.resolvePhysicalPlaceId(participantPosition)
                    .or(() -> environmentDataService.resolveLogicalPlaceId(participantPosition))
                    .orElse(participantPosition);

            log.info("[MOVEMENT] Resolved '{}' to participant position '{}'", trimmed, resolved);
            return resolved;
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
}