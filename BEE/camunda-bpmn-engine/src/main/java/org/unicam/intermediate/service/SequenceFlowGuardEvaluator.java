package org.unicam.intermediate.service;

import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.model.bpmn.instance.ExtensionElements;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.bpm.model.xml.instance.DomElement;
import org.springframework.stereotype.Service;
import org.unicam.intermediate.service.environmental.EnvironmentDataService;
import org.unicam.intermediate.service.participant.ParticipantDataService;
import org.unicam.intermediate.service.participant.ParticipantService;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Evaluates space:guard expressions on sequence flows.
 * Used by DynamicParseListener to determine if a sequence flow should be followed.
 */
@Service
@Slf4j
public class SequenceFlowGuardEvaluator {

    private static final Pattern GUARD_PATTERN = Pattern.compile(
            "^(.+?)\\.([A-Za-z0-9_-]+)\\s*(==|!=|>=|<=|>|<)\\s*(.+)$"
    );

    private final EnvironmentDataService environmentDataService;
    private final RepositoryService repositoryService;
    private final RuntimeService runtimeService;
    private final ParticipantDataService participantDataService;
    private final ParticipantService participantService;

    public SequenceFlowGuardEvaluator(EnvironmentDataService environmentDataService,
                                      RepositoryService repositoryService,
                                      RuntimeService runtimeService,
                                      ParticipantDataService participantDataService,
                                      ParticipantService participantService) {
        this.environmentDataService = environmentDataService;
        this.repositoryService = repositoryService;
        this.runtimeService = runtimeService;
        this.participantDataService = participantDataService;
        this.participantService = participantService;
    }

    /**
     * Evaluate guard for a specific sequence flow.
     * Called from BPMN condition expressions in DynamicParseListener like:
     * ${sequenceFlowGuardEvaluator.evaluateGuard(execution, 'Flow_123')}
     *
     * @param execution The current execution
     * @param sequenceFlowId The ID of the sequence flow being evaluated
     * @return true if guard passes (or no guard defined), false if guard fails
     */
    public Boolean evaluateGuard(DelegateExecution execution, String sequenceFlowId) {
        if (execution == null) {
            return false;
        }

        String participantId = null;
        // Try to resolve participant from process variable if available.
        Object runtimeParticipant = execution.getVariable("participantId");
        if (runtimeParticipant != null) {
            participantId = String.valueOf(runtimeParticipant);
        }

        // Fallback: resolve participant from collaboration/process context.
        if (participantId == null || participantId.isBlank()) {
            try {
                org.unicam.intermediate.models.Participant resolvedParticipant = participantService.resolveCurrentParticipant(execution);
                if (resolvedParticipant != null) {
                    participantId = resolvedParticipant.getId();
                }
            } catch (Exception ex) {
                log.debug("[SequenceFlowGuardEvaluator] Could not resolve participant from execution context", ex);
            }
        }

        return evaluateGuard(execution.getProcessDefinitionId(), sequenceFlowId, participantId);
    }

    public Boolean evaluateGuard(String processDefinitionId, String sequenceFlowId) {
        return evaluateGuard(processDefinitionId, sequenceFlowId, null);
    }

    public Boolean evaluateGuard(String processDefinitionId,
                                String sequenceFlowId,
                                String participantId,
                                String executionId) {
        String effectiveParticipantId = participantId;

        if ((effectiveParticipantId == null || effectiveParticipantId.isBlank())
                && executionId != null && !executionId.isBlank()) {
            try {
                Object runtimeParticipant = runtimeService.getVariable(executionId, "participantId");
                if (runtimeParticipant != null) {
                    effectiveParticipantId = String.valueOf(runtimeParticipant);
                }
            } catch (Exception ex) {
                log.debug("[SequenceFlowGuardEvaluator] Could not read participantId from execution {}", executionId, ex);
            }
        }

        return evaluateGuard(processDefinitionId, sequenceFlowId, effectiveParticipantId);
    }

    public Boolean evaluateGuard(String processDefinitionId, String sequenceFlowId, String participantId) {
        try {
            log.debug("[SequenceFlowGuardEvaluator] Evaluating guard for sequence flow: {}", sequenceFlowId);

            if (processDefinitionId == null || processDefinitionId.isBlank()) {
                log.warn("[SequenceFlowGuardEvaluator] Process definition ID not found");
                return true;
            }

            // Get BPMN model
            var bpmnModelInstance = repositoryService.getBpmnModelInstance(processDefinitionId);

            if (bpmnModelInstance == null) {
                log.debug("[SequenceFlowGuardEvaluator] BPMN model not found for process {}", processDefinitionId);
                return true; // No guard = pass through
            }

            // Find the sequence flow by ID
            var sequenceFlow = bpmnModelInstance.getModelElementById(sequenceFlowId);
            if (sequenceFlow == null) {
                log.debug("[SequenceFlowGuardEvaluator] Sequence flow not found: {}", sequenceFlowId);
                return true; // No guard = pass through
            }

            // Extract space:guard from extensionElements
            String guardExpression = extractGuardFromElement(sequenceFlow);

            if (guardExpression == null || guardExpression.trim().isEmpty()) {
                log.debug("[SequenceFlowGuardEvaluator] No guard defined for sequence flow: {}", sequenceFlowId);
                return true; // No guard = pass through
            }

            // Evaluate the guard expression
            log.debug("[SequenceFlowGuardEvaluator] Evaluating guard for {}: '{}'", sequenceFlowId, guardExpression);
            boolean result = evaluateGuardExpression(processDefinitionId, guardExpression, sequenceFlowId, participantId);

            log.info("[SequenceFlowGuardEvaluator] Guard evaluation for {} ({}): {}",
                    sequenceFlowId, guardExpression, result);

            return result;

        } catch (Exception e) {
            log.error("[SequenceFlowGuardEvaluator] Error evaluating guard for sequence flow {}: {}",
                    sequenceFlowId, e.getMessage(), e);
            return false; // Fail safe: if error during guard evaluation, don't follow the flow
        }
    }

    /**
     * Evaluate an ad-hoc guard expression bound to a BPMN source element
     * (e.g. a StartEvent with space:guard extension).
     */
    public Boolean evaluateAdHocGuard(String processDefinitionId,
                                      String sourceId,
                                      String guardExpression,
                                      String participantId) {
        if (guardExpression == null || guardExpression.isBlank()) {
            return true;
        }

        try {
            return evaluateGuardExpression(processDefinitionId, guardExpression, sourceId, participantId);
        } catch (Exception e) {
            log.error("[SequenceFlowGuardEvaluator] Error evaluating ad-hoc guard for {}: {}",
                    sourceId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Extract space:guard value from element's extensionElements
     */
    private String extractGuardFromElement(Object element) {
        if (!(element instanceof SequenceFlow sequenceFlow)) {
            return null;
        }

        ExtensionElements extensionElements = sequenceFlow.getExtensionElements();
        if (extensionElements == null || extensionElements.getDomElement() == null) {
            return null;
        }

        return extensionElements.getDomElement().getChildElements().stream()
                .filter(this::isSpaceGuard)
                .map(DomElement::getTextContent)
                .map(String::trim)
                .findFirst()
                .orElse(null);
    }

    private boolean isSpaceGuard(DomElement domElement) {
        return "guard".equalsIgnoreCase(domElement.getLocalName())
                && "http://space".equals(domElement.getNamespaceURI());
    }

    /**
     * Evaluate a guard expression like "place1.attribute == value"
     */
    private boolean evaluateGuardExpression(String processDefinitionId, String guardExpression, String sourceId, String participantId) {
        if (guardExpression == null || guardExpression.isBlank()) {
            return true;
        }

        String resolvedExpression = resolveMyPlace(guardExpression, processDefinitionId, participantId, sourceId);
        if (guardExpression.contains("myPlace()") && resolvedExpression.contains("myPlace()")) {
            log.debug("[SequenceFlowGuardEvaluator] Waiting for participant position to resolve myPlace() for {} | participantId={} | processDefinitionId={} | expression='{}'",
                sourceId,
                participantId,
                processDefinitionId,
                guardExpression);
            return false;
        }

        Matcher matcher = GUARD_PATTERN.matcher(resolvedExpression.trim());
        if (!matcher.matches()) {
            log.warn("[SequenceFlowGuardEvaluator] Invalid guard format for {}: '{}'", sourceId, resolvedExpression);
            return false;
        }

        String reference = matcher.group(1).trim();
        String attributeKey = matcher.group(2);
        String operator = matcher.group(3);
        String expectedRaw = unquote(matcher.group(4).trim());

        if ("position".equals(attributeKey)) {
            return evaluateParticipantPosition(processDefinitionId, reference, operator, expectedRaw, sourceId);
        }

        Optional<String> logicalPlaceId = environmentDataService.resolveLogicalPlaceId(reference);
        if (logicalPlaceId.isPresent()) {
            Optional<Object> logicalGuardValue = resolveLogicalPlaceGuardValue(logicalPlaceId.get(), attributeKey);
            if (logicalGuardValue.isPresent()) {
                boolean result = compare(logicalGuardValue.get(), operator, expectedRaw);
                log.debug("[SequenceFlowGuardEvaluator] Logical guard evaluation | source={} | logical='{}' | attr='{}' | actual='{}' | op='{}' | expected='{}' -> {}",
                        sourceId, logicalPlaceId.get(), attributeKey, logicalGuardValue.get(), operator, expectedRaw, result);
                return result;
            }
        }

        Optional<Object> actualValueOpt = environmentDataService.getPhysicalPlaceAttribute(reference, attributeKey);
        if (actualValueOpt.isEmpty()) {
            log.debug("[SequenceFlowGuardEvaluator] Attribute '{}.{}' not found for {}", reference, attributeKey, sourceId);
            return false;
        }

        Object actualValue = actualValueOpt.get();
        boolean result = compare(actualValue, operator, expectedRaw);

        log.debug("[SequenceFlowGuardEvaluator] Guard evaluation | source={} | expr='{}' | actual='{}' -> {}",
                sourceId, resolvedExpression, actualValue, result);

        return result;
    }

    private boolean evaluateParticipantPosition(String processDefinitionId,
                                                String participantRef,
                                                String operator,
                                                String expectedPlaceRef,
                                                String sourceId) {
        String resolvedParticipantRef = participantService.resolveParticipantId(processDefinitionId, participantRef);

        Optional<String> currentPositionOpt = participantDataService.getParticipant(resolvedParticipantRef)
            .map(p -> p.getPosition());

        if (currentPositionOpt.isEmpty() || currentPositionOpt.get() == null) {
            log.debug("[SequenceFlowGuardEvaluator] Position check: participant '{}' not found or has no position for {}",
                participantRef, sourceId);
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

            log.debug("[SequenceFlowGuardEvaluator] Position guard (logical) | source={} | participant='{}' | position='{}' | op='{}' | expected='{}' -> {}",
                    sourceId, resolvedParticipantRef, currentPosition, operator, resolvedLogicalExpected.get(), result);

            return result;
        }

        String resolvedExpected = environmentDataService.resolvePhysicalPlaceId(expectedPlaceRef)
            .or(() -> environmentDataService.resolveLogicalPlaceId(expectedPlaceRef))
            .orElse(expectedPlaceRef);
        String resolvedPosition = environmentDataService.resolvePhysicalPlaceId(currentPosition)
            .orElse(currentPosition);

        boolean result = compare(resolvedPosition, operator, resolvedExpected);

        log.debug("[SequenceFlowGuardEvaluator] Position guard | source={} | participant='{}' | position='{}' | op='{}' | expected='{}' -> {}",
            sourceId, resolvedParticipantRef, resolvedPosition, operator, resolvedExpected, result);

        return result;
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

    private String resolveMyPlace(String expression, String processDefinitionId, String participantId, String sourceId) {
        if (expression == null || !expression.contains("myPlace()")) {
            return expression;
        }

        if (participantId == null || participantId.isBlank()) {
            log.warn("[SequenceFlowGuardEvaluator] myPlace() used but no participantId available for {}", sourceId);
            return expression;
        }

        Optional<org.unicam.intermediate.models.pojo.Participant> participantOpt = participantDataService.getParticipant(participantId);

        if (participantOpt.isEmpty()) {
            String participantName = participantService.getParticipantName(processDefinitionId, participantId);
            if (participantName != null && !participantName.equals(participantId)) {
                participantOpt = participantDataService.getParticipantByName(participantName);
                if (participantOpt.isPresent()) {
                    log.debug("[SequenceFlowGuardEvaluator] Resolved runtime participant by name '{}' for BPMN id '{}' on {}",
                            participantName, participantId, sourceId);
                }
            }
        }

        return participantOpt
            .map(participant -> {
                String position = participant.getPosition();
                if (position == null || position.isBlank()) {
                    log.warn("[SequenceFlowGuardEvaluator] myPlace() used but participant '{}' has no position for {}",
                            participantId, sourceId);
                    return expression;
                }
                return expression.replace("myPlace()", position);
            })
            .orElseGet(() -> {
                log.warn("[SequenceFlowGuardEvaluator] myPlace() used but participant '{}' not found for {}",
                        participantId, sourceId);
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
}
