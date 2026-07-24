package org.unicam.intermediate.service.participant;

import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.*;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.Collection;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class ParticipantService {

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private RuntimeService runtimeService;

    // Cache collaboration data per business key to avoid repeated BPMN parsing
    private final Map<String, CollaborationData> collaborationCache = new ConcurrentHashMap<>();

    /**
     * Resolves the current participant from the execution context
     */
    public org.unicam.intermediate.models.Participant resolveCurrentParticipant(DelegateExecution execution) {
        CollaborationData collabData = getCollaborationData(execution);
        String currentProcessKey = getCurrentProcessKey(execution);

        return collabData.getParticipantByProcessKey(currentProcessKey);
    }

    /**
     * Resolves the target participant from the binding specification
     */
    public org.unicam.intermediate.models.Participant resolveTargetParticipant(DelegateExecution execution, String bindingSpec) {
        CollaborationData collabData = getCollaborationData(execution);

        // If bindingSpec is a direct participant ID from BPMN, use it
        org.unicam.intermediate.models.Participant directMatch = collabData.getParticipantById(bindingSpec);
        if (directMatch != null) {
            return directMatch;
        }

        // If it's a process key, resolve by process
        org.unicam.intermediate.models.Participant processMatch = collabData.getParticipantByProcessKey(bindingSpec);
        if (processMatch != null) {
            return processMatch;
        }

        // Fallback: assume it's the "other" participant in a 2-participant collaboration
        String currentProcessKey = getCurrentProcessKey(execution);
        return collabData.getOtherParticipant(currentProcessKey);
    }

    /**
     * Get participant by ID from the collaboration
     */
    public org.unicam.intermediate.models.Participant getParticipantById(DelegateExecution execution, String participantId) {
        if (participantId == null) {
            return null;
        }

        CollaborationData collabData = getCollaborationData(execution);
        return collabData.getParticipantById(participantId);
    }

    public String resolveParticipantForTask(Task task) {
        try {
            // 1. Prima prova dalle variabili del processo
            ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(task.getProcessInstanceId())
                    .singleResult();

            if (pi == null) {
                log.warn("[ParticipantService] No process instance found for task {}", task.getId());
                return "Participant_Unknown";
            }

            // Check process variables first
            Object participantId = runtimeService.getVariable(pi.getId(), "participantId");
            if (participantId != null) {
                log.debug("[ParticipantService] Found participantId in variables: {}", participantId);
                return participantId.toString();
            }

            // 2. Ottieni il modello BPMN
            BpmnModelInstance model = repositoryService.getBpmnModelInstance(task.getProcessDefinitionId());

            // 3. Trova il task nel modello
            ModelElementInstance taskElement = model.getModelElementById(task.getTaskDefinitionKey());
            if (taskElement == null) {
                log.warn("[ParticipantService] Task element {} not found in BPMN", task.getTaskDefinitionKey());
                return "Participant_Default";
            }

            // 4. Risali al processo che contiene questo task
            if (taskElement instanceof FlowNode) {
                FlowNode flowNode = (FlowNode) taskElement;

                // Ottieni il subprocess o processo padre
                BaseElement container = (BaseElement) flowNode.getParentElement();
                while (container != null && !(container instanceof Process)) {
                    container = (BaseElement) container.getParentElement();
                }

                if (container instanceof Process) {
                    Process process = (Process) container;

                    // 5. Trova il participant che referenzia questo processo
                    Collection<Participant> participants = model.getModelElementsByType(Participant.class);
                    for (Participant participant : participants) {
                        if (participant.getProcess() != null &&
                                participant.getProcess().getId().equals(process.getId())) {

                            log.info("[ParticipantService] Resolved participant {} for task {} in process {}",
                                    participant.getId(), task.getTaskDefinitionKey(), process.getId());

                            return participant.getId();
                        }
                    }
                }
            }

            // 6. Se siamo in un processo senza collaboration, usa un default basato sul processo
            String processKey = repositoryService.getProcessDefinition(task.getProcessDefinitionId()).getKey();
            String defaultParticipant = "Participant_" + processKey;

            log.info("[ParticipantService] No participant found in model, using default: {}", defaultParticipant);
            return defaultParticipant;

        } catch (Exception e) {
            log.error("[ParticipantService] Error resolving participant for task {}", task.getId(), e);
            return "Participant_Error";
        }
    }

    /**
     * Metodo alternativo che usa l'execution context invece del task
     */
    public String resolveParticipantFromExecution(DelegateExecution execution) {
        try {
            String processDefinitionId = execution.getProcessDefinitionId();
            BpmnModelInstance model = repositoryService.getBpmnModelInstance(processDefinitionId);

            // Ottieni il processo corrente dall'execution
            String currentProcessId = execution.getProcessDefinitionId()
                    .substring(0, execution.getProcessDefinitionId().indexOf(':'));

            // Trova la collaboration
            Collection<Collaboration> collaborations = model.getModelElementsByType(Collaboration.class);
            if (collaborations.isEmpty()) {
                log.debug("[ParticipantService] No collaboration found, single participant process");
                return "Participant_" + currentProcessId;
            }

            Collaboration collaboration = collaborations.iterator().next();

            // Trova il participant per questo processo
            for (Participant participant : collaboration.getParticipants()) {
                Process process = participant.getProcess();
                if (process != null && process.getId().equals(currentProcessId)) {
                    log.debug("[ParticipantService] Found participant {} for process {}",
                            participant.getId(), currentProcessId);
                    return participant.getId();
                }
            }

            // Fallback
            return "Participant_" + currentProcessId;

        } catch (Exception e) {
            log.error("[ParticipantService] Error resolving participant from execution", e);
            return "Participant_Unknown";
        }
    }

    public String getParticipantName(String processDefinitionId, String participantId) {
        try {
            BpmnModelInstance model = repositoryService.getBpmnModelInstance(processDefinitionId);
            Participant participant = model.getModelElementById(participantId);

            if (participant != null && participant.getName() != null) {
                return participant.getName();
            }
        } catch (Exception e) {
            log.debug("[ParticipantService] Error getting participant name", e);
        }

        return participantId;
    }

    public String resolveParticipantId(String processDefinitionId, String participantReference) {
        if (participantReference == null || participantReference.isBlank() || processDefinitionId == null || processDefinitionId.isBlank()) {
            return participantReference;
        }

        try {
            BpmnModelInstance model = repositoryService.getBpmnModelInstance(processDefinitionId);
            Collection<Participant> participants = model.getModelElementsByType(Participant.class);

            for (Participant participant : participants) {
                if (participantReference.equals(participant.getId())) {
                    return participant.getId();
                }

                String participantName = participant.getName();
                if (participantName != null && participantReference.equalsIgnoreCase(participantName)) {
                    return participant.getId();
                }
            }
        } catch (Exception e) {
            log.debug("[ParticipantService] Error resolving participant reference '{}'", participantReference, e);
        }

        return participantReference;
    }

    /**
     * Gets all participants in the current collaboration
     */
    public List<org.unicam.intermediate.models.Participant> getAllParticipants(DelegateExecution execution) {
        CollaborationData collabData = getCollaborationData(execution);
        return new ArrayList<>(collabData.getAllParticipants());
    }

    private CollaborationData getCollaborationData(DelegateExecution execution) {
        String businessKey = execution.getBusinessKey();
        String cacheKey = businessKey != null ? businessKey : execution.getProcessInstanceId();

        return collaborationCache.computeIfAbsent(cacheKey, k -> extractCollaborationData(execution));
    }

    private CollaborationData extractCollaborationData(DelegateExecution execution) {
        try {
            // Get the BPMN model from any process in this collaboration
            String processDefinitionId = execution.getProcessDefinitionId();
            BpmnModelInstance model = repositoryService.getBpmnModelInstance(processDefinitionId);

            // Find the collaboration
            Collection<Collaboration> collaborations = model.getModelElementsByType(Collaboration.class);
            if (collaborations.isEmpty()) {
                log.warn("[ParticipantService] No collaboration found in BPMN model, creating single participant");
                return createSingleParticipantCollaboration(execution);
            }

            Collaboration collaboration = collaborations.iterator().next();
            Collection<Participant> bpmnParticipants = collaboration.getParticipants();

            CollaborationData collabData = new CollaborationData();

            for (Participant bpmnParticipant : bpmnParticipants) {
                Process process = bpmnParticipant.getProcess();
                if (process != null) {
                    String participantId = bpmnParticipant.getId();
                    String participantName = bpmnParticipant.getName();
                    String processKey = process.getId();

                    org.unicam.intermediate.models.Participant participant =
                            new org.unicam.intermediate.models.Participant(
                                    participantId,
                                    extractRole(participantName),
                                    participantName != null ? participantName : participantId,
                                    processKey
                            );

                    collabData.addParticipant(participant);

                    log.debug("[ParticipantService] Found participant: {} -> {} ({})",
                            participantId, participantName, processKey);
                }
            }

            log.info("[ParticipantService] Extracted collaboration with {} participants",
                    collabData.getParticipantCount());

            return collabData;

        } catch (Exception e) {
            log.error("[ParticipantService] Failed to extract collaboration data: {}", e.getMessage(), e);
            return createSingleParticipantCollaboration(execution);
        }
    }

    private CollaborationData createSingleParticipantCollaboration(DelegateExecution execution) {
        CollaborationData collabData = new CollaborationData();
        String processKey = getCurrentProcessKey(execution);

        org.unicam.intermediate.models.Participant singleParticipant =
                new org.unicam.intermediate.models.Participant(
                        "Participant_" + processKey,
                        processKey.toLowerCase(),
                        processKey,
                        processKey
                );

        collabData.addParticipant(singleParticipant);
        return collabData;
    }

    private String getCurrentProcessKey(DelegateExecution execution) {
        try {
            return repositoryService.getProcessDefinition(execution.getProcessDefinitionId()).getKey();
        } catch (Exception e) {
            log.debug("[ParticipantService] Could not get process key: {}", e.getMessage());
            return "UnknownProcess";
        }
    }

    private String extractRole(String participantName) {
        if (participantName == null || participantName.trim().isEmpty()) {
            return "participant";
        }
        return participantName.toLowerCase().replaceAll("[^a-z0-9]", "_");
    }

    /**
     * Clear cache when processes end or new deployments occur
     */
    public void clearCache() {
        collaborationCache.clear();
        log.info("[ParticipantService] Collaboration cache cleared");
    }

    /**
     * Internal class to hold collaboration data
     */
    private static class CollaborationData {
        private final Map<String, org.unicam.intermediate.models.Participant> participantsById = new HashMap<>();
        private final Map<String, org.unicam.intermediate.models.Participant> participantsByProcessKey = new HashMap<>();
        private final List<org.unicam.intermediate.models.Participant> allParticipants = new ArrayList<>();

        void addParticipant(org.unicam.intermediate.models.Participant participant) {
            participantsById.put(participant.getId(), participant);
            participantsByProcessKey.put(participant.getProcessDefinitionKey(), participant);
            allParticipants.add(participant);
        }

        org.unicam.intermediate.models.Participant getParticipantById(String participantId) {
            return participantsById.get(participantId);
        }

        org.unicam.intermediate.models.Participant getParticipantByProcessKey(String processKey) {
            return participantsByProcessKey.get(processKey);
        }

        org.unicam.intermediate.models.Participant getOtherParticipant(String currentProcessKey) {
            return allParticipants.stream()
                    .filter(p -> !p.getProcessDefinitionKey().equals(currentProcessKey))
                    .findFirst()
                    .orElse(null);
        }

        List<org.unicam.intermediate.models.Participant> getAllParticipants() {
            return Collections.unmodifiableList(allParticipants);
        }

        int getParticipantCount() {
            return allParticipants.size();
        }
    }
}