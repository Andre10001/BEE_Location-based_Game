// src/main/java/org/unicam/intermediate/service/task/TaskTrackingService.java

package org.unicam.intermediate.service.task;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.ExtensionElements;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.springframework.stereotype.Service;
import org.unicam.intermediate.service.TaskAuthorizationService;
import org.unicam.intermediate.service.participant.ParticipantService;
import org.unicam.intermediate.service.participant.UserParticipantMappingService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@AllArgsConstructor
public class TaskTrackingService {

    private final TaskService taskService;
    private final RuntimeService runtimeService;
    private final ParticipantService participantService;
    private final UserParticipantMappingService userParticipantMapping;
    private final RepositoryService repositoryService;
    private final TaskAuthorizationService taskAuthorizationService;

    public boolean canUserAccessTask(String userId, Task task) {
        return taskAuthorizationService.canUserAccessTask(userId, task);
    }

    public void clearTrackingIfTaskMatches(String userId, String taskId) {
        UserParticipantMappingService.TrackingContext context =
                userParticipantMapping.getActiveTracking(userId);

        if (context != null && taskId.equals(context.getTaskId())) {
            userParticipantMapping.clearTracking(userId);
            log.info("[Task Tracking] Cleared tracking for user {} after completing task {}",
                    userId, taskId);
        }
    }

    /**
     * Start tracking for a specific task
     */
    public Map<String, Object> startTrackingForTask(String taskId, String userId) {
        Task task = taskService.createTaskQuery()
                .taskId(taskId)
                .singleResult();

        if (task == null) {
            throw new IllegalArgumentException("Task not found: " + taskId);
        }

        ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                .processInstanceId(task.getProcessInstanceId())
                .singleResult();

        String businessKey = pi != null ? pi.getBusinessKey() : "";

        // Resolve participant for this task
        String participantId = participantService.resolveParticipantForTask(task);
        String participantName = participantService.getParticipantName(
                task.getProcessDefinitionId(), participantId);

        // Store the active tracking session
        userParticipantMapping.setActiveTracking(
                userId,
                taskId,
                task.getProcessInstanceId(),
                businessKey,
                participantId,
                participantName
        );

        Map<String, Object> trackingInfo = new HashMap<>();
        trackingInfo.put("taskId", taskId);
        trackingInfo.put("processInstanceId", task.getProcessInstanceId());
        trackingInfo.put("businessKey", businessKey);
        trackingInfo.put("participantId", participantId);
        trackingInfo.put("participantName", participantName);
        trackingInfo.put("taskName", task.getName() != null ? task.getName() : "");

        log.info("[Task Tracking] User {} started tracking task {} as participant {}",
                userId, taskId, participantId);

        return trackingInfo;
    }

    /**
     * Stop tracking for a user
     */
    public void stopTracking(String userId) {
        userParticipantMapping.clearTracking(userId);
        log.info("[Task Tracking] Stopped tracking for user {}", userId);
    }

    /**
     * Get current tracking status for a user
     */
    public Map<String, Object> getTrackingStatus(String userId) {
        UserParticipantMappingService.TrackingContext context =
                userParticipantMapping.getActiveTracking(userId);

        Map<String, Object> status = new HashMap<>();

        if (context != null) {
            status.put("isTracking", true);
            status.put("taskId", context.getTaskId());
            status.put("processInstanceId", context.getProcessInstanceId());
            status.put("businessKey", context.getBusinessKey());
            status.put("participantId", context.getParticipantId());
            status.put("participantName", context.getParticipantName());
            status.put("startedAt", context.getStartedAt());
        } else {
            status.put("isTracking", false);
        }

        return status;
    }

    public Map<String, Object> enrichedTaskToMap(Task task) {
        Map<String, Object> taskMap = new HashMap<>();

        // Informazioni base del task
        taskMap.put("id", task.getId());
        taskMap.put("name", task.getName());
        taskMap.put("assignee", task.getAssignee());
        taskMap.put("processInstanceId", task.getProcessInstanceId());
        taskMap.put("taskDefinitionKey", task.getTaskDefinitionKey());
        taskMap.put("createTime", task.getCreateTime());
        taskMap.put("priority", task.getPriority());

        try {
            // Aggiungi business key
            ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(task.getProcessInstanceId())
                    .singleResult();

            if (pi != null) {
                taskMap.put("businessKey", pi.getBusinessKey());

                // Participant info
                String participantId = participantService.resolveParticipantForTask(task);
                taskMap.put("participantId", participantId);

                String participantName = participantService.getParticipantName(
                        task.getProcessDefinitionId(), participantId);
                taskMap.put("participantName", participantName);
            }

            // Aggiungi tipo di task (movement, binding, unbinding)
            String taskType = determineTaskType(task);
            taskMap.put("taskType", taskType);

            // Se è un movement task, aggiungi la destinazione
            if ("MOVEMENT".equals(taskType)) {
                String destinationKey = task.getTaskDefinitionKey() + ".destination";
                Object destination = runtimeService.getVariable(
                        task.getExecutionId(), destinationKey);
                if (destination != null) {
                    taskMap.put("destination", destination.toString());
                }
            }

        } catch (Exception e) {
            log.error("[TaskTracking] Error enriching task {}: {}", task.getId(), e.getMessage());
        }

        return taskMap;
    }

    private String determineTaskType(Task task) {
        try {
            BpmnModelInstance model = repositoryService.getBpmnModelInstance(
                    task.getProcessDefinitionId());
            ModelElementInstance element = model.getModelElementById(task.getTaskDefinitionKey());

            if (element instanceof FlowNode) {
                ExtensionElements extensions = ((FlowNode) element).getExtensionElements();
                if (extensions != null) {
                    // Cerca il tipo nelle extension
                    return extensions.getDomElement().getChildElements().stream()
                            .filter(dom -> "type".equals(dom.getLocalName()))
                            .map(dom -> dom.getTextContent().trim().toUpperCase())
                            .findFirst()
                            .orElse("STANDARD");
                }
            }
        } catch (Exception e) {
            log.debug("[TaskTracking] Could not determine task type for {}", task.getId());
        }
        return "STANDARD";
    }
}