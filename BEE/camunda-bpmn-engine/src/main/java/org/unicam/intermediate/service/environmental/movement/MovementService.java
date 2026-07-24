package org.unicam.intermediate.service.environmental.movement;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.engine.runtime.Execution;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.ExtensionElements;
import org.camunda.bpm.model.bpmn.instance.Task;
import org.camunda.bpm.model.bpmn.instance.UserTask;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.springframework.stereotype.Service;
import org.unicam.intermediate.models.enums.TaskType;
import org.unicam.intermediate.service.TaskTypeRegistry;
import org.unicam.intermediate.utils.Constants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@AllArgsConstructor
@Service
@Slf4j
public class MovementService {

    private final RepositoryService repositoryService;
    private final RuntimeService runtimeService;
    private final IdentityService identityService;
    private final TaskTypeRegistry taskTypeRegistry;

    /**
     * Checks if a process definition has any tasks with the specified space:type
     */
    public boolean hasTasksOfType(ProcessDefinition definition, TaskType taskType) {
        BpmnModelInstance instance = repositoryService.getBpmnModelInstance(definition.getId());

        return instance
                .getModelElementsByType(Task.class)
                .stream()
                .anyMatch(task -> hasSpaceTypeValue(task, taskType));
    }


    public List<ProcessDefinition> getActiveProcessDefinitionsWithMovementTasks() {
        return repositoryService
                .createProcessDefinitionQuery()
                .active()
                .list()
                .stream()
                .filter(def -> hasTasksOfType(def, TaskType.MOVEMENT))
                .collect(Collectors.toList());
    }

    /**
     * Gets all task IDs that have the specified space:type
     */
    public List<String> getTasksOfType(ProcessDefinition definition, TaskType taskType) {
        BpmnModelInstance model = repositoryService.getBpmnModelInstance(definition.getId());

        return model.getModelElementsByType(Task.class).stream()
                .filter(task -> hasSpaceTypeValue(task, taskType))
                .map(Task::getId)
                .collect(Collectors.toList());
    }

    /**
     * Gets all tasks of any registered type from the registry
     */
    public List<String> getAllDynamicTasks(ProcessDefinition definition) {
        BpmnModelInstance model = repositoryService.getBpmnModelInstance(definition.getId());
        List<String> dynamicTasks = new ArrayList<>();

        for (Task task : model.getModelElementsByType(Task.class)) {
            String spaceType = extractSpaceTypeValue(task);
            if (spaceType != null && taskTypeRegistry.isRegisteredTaskType(spaceType)) {
                dynamicTasks.add(task.getId());
            }
        }

        log.debug("[MovementService] Found {} dynamic tasks in process definition {}", 
                dynamicTasks.size(), definition.getKey());
        return dynamicTasks;
    }

    /**
     * Finds active executions for activities with the specified space:type
     */
    public List<Execution> findActiveExecutionsForTaskType(
            String processDefinitionId,
            TaskType taskType,
            String userId) {

        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(processDefinitionId)
                .singleResult();

        List<String> taskIds = getTasksOfType(definition, taskType);
        return findActiveExecutionsForActivities(processDefinitionId, taskIds, userId);
    }

    /**
     * Finds active executions for the specified activity IDs with user authorization
     */
    public List<Execution> findActiveExecutionsForActivities(
            String processDefinitionId,
            List<String> activityIds,
            String userId) {

        List<Execution> executions = new ArrayList<>();
        for (String taskId : activityIds) {
            List<Execution> execs = runtimeService.createExecutionQuery()
                    .processDefinitionId(processDefinitionId)
                    .activityId(taskId)
                    .active()
                    .list();

            for (Execution exe : execs) {
                if (!isUserAuthorizedOnTask(processDefinitionId, taskId, userId)) {
                    continue;
                }
                executions.add(exe);
            }
        }
        return executions;
    }

    private boolean hasSpaceTypeValue(Task task, TaskType expectedType) {
        String actualType = extractSpaceTypeValue(task);
        return expectedType.toString().equals(actualType);
    }

    private String extractSpaceTypeValue(Task task) {
        ExtensionElements ext = task.getExtensionElements();
        if (ext == null) return null;
        
        return ext.getDomElement().getChildElements().stream()
                .filter(dom -> "type".equals(dom.getLocalName()) 
                        && Constants.SPACE_NS.getNamespaceUri().equals(dom.getNamespaceURI()))
                .map(dom -> dom.getTextContent().trim())
                .findFirst()
                .orElse(null);
    }

    private boolean isUserAuthorizedOnTask(String processDefinitionId,
                                           String taskId,
                                           String userId) {

        BpmnModelInstance model = repositoryService.getBpmnModelInstance(processDefinitionId);
        ModelElementInstance elem = model.getModelElementById(taskId);
        if (!(elem instanceof UserTask userTask)) {
            return true;
        }

        // Check candidateUsers
        String users = userTask.getCamundaCandidateUsers();
        if (users != null && !users.isBlank()) {
            return Arrays.stream(users.split(","))
                    .map(String::trim)
                    .anyMatch(u -> u.equalsIgnoreCase(userId));
        }

        // Check candidateGroups
        String groups = userTask.getCamundaCandidateGroups();
        if (groups != null && !groups.isBlank()) {
            Set<String> userGroups = identityService.createGroupQuery()
                    .groupMember(userId)
                    .list().stream()
                    .map(Group::getId)
                    .collect(Collectors.toSet());
            return Arrays.stream(groups.split(","))
                    .map(String::trim)
                    .anyMatch(userGroups::contains);
        }

        return true;
    }
}