package org.unicam.intermediate.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.task.IdentityLink;
import org.camunda.bpm.engine.task.Task;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@AllArgsConstructor
public class TaskAuthorizationService {
    
    private final TaskService taskService;
    private final IdentityService identityService;
    
    /**
     * Check if a user can access a specific task
     */
    public boolean canUserAccessTask(String userId, Task task) {
        // Check if user is assignee
        if (userId.equals(task.getAssignee())) {
            return true;
        }

        // Check if user is candidate user
        List<IdentityLink> links = taskService.getIdentityLinksForTask(task.getId());
        for (IdentityLink link : links) {
            if ("candidate".equals(link.getType()) && userId.equals(link.getUserId())) {
                return true;
            }
        }

        // Check if user is in candidate group
        List<Group> userGroups = identityService.createGroupQuery()
                .groupMember(userId)
                .list();

        Set<String> userGroupIds = userGroups.stream()
                .map(Group::getId)
                .collect(Collectors.toSet());

        for (IdentityLink link : links) {
            if ("candidate".equals(link.getType()) && userGroupIds.contains(link.getGroupId())) {
                return true;
            }
        }

        return false;
    }
    
    /**
     * Check if a user can access a task by ID
     */
    public boolean canUserAccessTaskById(String userId, String taskId) {
        Task task = taskService.createTaskQuery()
                .taskId(taskId)
                .singleResult();
        
        if (task == null) {
            return false;
        }
        
        return canUserAccessTask(userId, task);
    }
}