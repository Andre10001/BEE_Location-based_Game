package org.unicam.intermediate.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.ExtensionElements;
import org.camunda.bpm.model.bpmn.instance.MessageFlow;
import org.camunda.bpm.model.bpmn.instance.Participant;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.xml.instance.DomElement;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class MessageFlowRegistry {

    @Autowired
    @Lazy  // Break circular dependency by lazy loading
    private RepositoryService repositoryService;

    // Key: processDefinitionId:taskId -> MessageFlowBinding
    private final Map<String, MessageFlowBinding> flowBindings = new ConcurrentHashMap<>();

    @Data
    public static class MessageFlowBinding {
        private String flowId;
        private String type; // binding/unbinding
        private String sourceParticipantRef;
        private String targetParticipantRef;
        private String sourceTaskRef;
        private String targetTaskRef;
    }

    /**
     * Register message flows for a process. Can be called during parsing or after deployment.
     */
    public void registerFlowsForProcess(String processDefinitionId, BpmnModelInstance model) {
        if (model == null) {
            log.warn("[MessageFlowRegistry] Model is null for process: {}", processDefinitionId);
            return;
        }

        model.getModelElementsByType(MessageFlow.class).forEach(flow -> {
            ExtensionElements ext = flow.getExtensionElements();
            if (ext == null) return;

            String flowType = extractExtensionValue(ext, "type");
            if (!"binding".equals(flowType) && !"unbinding".equals(flowType)) return;

            MessageFlowBinding binding = new MessageFlowBinding();
            binding.setFlowId(flow.getId());
            binding.setType(flowType);
            binding.setSourceParticipantRef(extractExtensionValue(ext, "sourceRef", "participant1", "Participant1"));
            binding.setTargetParticipantRef(extractExtensionValue(ext, "targetRef", "participant2", "Participant2"));

            // Get task references from the flow source and target
            if (flow.getSource() != null) {
                binding.setSourceTaskRef(flow.getSource().getId());
            }
            if (flow.getTarget() != null) {
                binding.setTargetTaskRef(flow.getTarget().getId());
            }

            // Fallback: if participant refs are not explicitly set in extension elements,
            // infer them from source/target task owning process and collaboration participants.
            if (isBlank(binding.getSourceParticipantRef()) && binding.getSourceTaskRef() != null) {
                binding.setSourceParticipantRef(resolveParticipantRefForTask(model, binding.getSourceTaskRef()));
            }
            if (isBlank(binding.getTargetParticipantRef()) && binding.getTargetTaskRef() != null) {
                binding.setTargetParticipantRef(resolveParticipantRefForTask(model, binding.getTargetTaskRef()));
            }

            // Register for both source and target tasks
            if (binding.getSourceTaskRef() != null) {
                String sourceKey = processDefinitionId + ":" + binding.getSourceTaskRef();
                flowBindings.put(sourceKey, binding);
            }

            if (binding.getTargetTaskRef() != null) {
                String targetKey = processDefinitionId + ":" + binding.getTargetTaskRef();
                flowBindings.put(targetKey, binding);
            }

            log.info("[MessageFlowRegistry] Registered {} flow: {} -> {} (participants: {} -> {})",
                    flowType, binding.getSourceTaskRef(), binding.getTargetTaskRef(),
                    binding.getSourceParticipantRef(), binding.getTargetParticipantRef());
        });
    }

    /**
     * Try to register flows after deployment if not done during parsing
     */
    public void ensureFlowsRegistered(String processDefinitionId) {
        if (!flowBindings.keySet().stream().anyMatch(k -> k.startsWith(processDefinitionId + ":"))) {
            try {
                if (repositoryService != null) {
                    BpmnModelInstance model = repositoryService.getBpmnModelInstance(processDefinitionId);
                    registerFlowsForProcess(processDefinitionId, model);
                    log.info("[MessageFlowRegistry] Late registration of flows for process: {}", processDefinitionId);
                }
            } catch (Exception e) {
                log.error("[MessageFlowRegistry] Failed to register flows for process {}: {}",
                        processDefinitionId, e.getMessage());
            }
        }
    }

    public MessageFlowBinding getFlowBinding(String processDefinitionId, String taskId) {
        // Ensure flows are registered (in case parse listener couldn't access repository)
        ensureFlowsRegistered(processDefinitionId);

        return flowBindings.get(processDefinitionId + ":" + taskId);
    }

    private String extractExtensionValue(ExtensionElements ext, String... localNames) {
        if (ext == null || localNames == null || localNames.length == 0) {
            return null;
        }

        return ext.getDomElement().getChildElements().stream()
                .filter(dom -> matchesAnyLocalName(dom.getLocalName(), localNames)
                        && "http://space".equals(dom.getNamespaceURI()))
                .map(DomElement::getTextContent)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    private boolean matchesAnyLocalName(String actualLocalName, String... acceptedNames) {
        if (actualLocalName == null || acceptedNames == null) {
            return false;
        }
        for (String expected : acceptedNames) {
            if (expected != null && expected.equalsIgnoreCase(actualLocalName)) {
                return true;
            }
        }
        return false;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String resolveParticipantRefForTask(BpmnModelInstance model, String taskId) {
        if (model == null || taskId == null || taskId.isBlank()) {
            return null;
        }

        ModelElementInstance element = model.getModelElementById(taskId);
        if (!(element instanceof FlowNode flowNode)) {
            return null;
        }

        ModelElementInstance container = flowNode.getParentElement();
        while (container != null && !(container instanceof Process)) {
            container = container.getParentElement();
        }

        if (!(container instanceof Process process)) {
            return null;
        }

        Collection<Participant> participants = model.getModelElementsByType(Participant.class);
        for (Participant participant : participants) {
            if (participant.getProcess() != null && process.getId().equals(participant.getProcess().getId())) {
                return participant.getId();
            }
        }

        return null;
    }

    /**
     * Clear registry (useful for testing or redeployment)
     */
    public void clear() {
        flowBindings.clear();
        log.info("[MessageFlowRegistry] Cleared all flow bindings");
    }
}