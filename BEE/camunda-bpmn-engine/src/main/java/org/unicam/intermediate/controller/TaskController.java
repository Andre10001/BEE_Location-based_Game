// src/main/java/org/unicam/intermediate/controller/TaskController.java

package org.unicam.intermediate.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.variable.Variables;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.unicam.intermediate.models.dto.Response;
import org.unicam.intermediate.service.task.TaskTrackingService;

import java.util.*;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
@Slf4j
public class TaskController {

    private final TaskService taskService;
    private final IdentityService identityService;
    private final TaskTrackingService taskTrackingService;

    /**
     * Get all tasks accessible by a user (assigned, candidate user, or candidate group)
     * Enhanced with participant information and movement task details
     */
    @GetMapping("/getTasksByUser")
    public ResponseEntity<Response<List<Map<String, Object>>>> getTasksByUser(@RequestParam("userId") String userId) {
        try {
            if (userId == null || userId.isBlank()) {
                return ResponseEntity.badRequest().body(Response.error("userId is required"));
            }

            Set<String> seenTaskIds = new HashSet<>();
            List<Map<String, Object>> result = new ArrayList<>();

            // Get user's groups
            List<Group> userGroups = identityService.createGroupQuery()
                    .groupMember(userId)
                    .list();
            List<String> groupIds = userGroups.stream().map(Group::getId).toList();

            // Tasks assigned to user
            taskService.createTaskQuery()
                    .taskAssignee(userId)
                    .active()
                    .list()
                    .forEach(task -> {
                        if (seenTaskIds.add(task.getId())) {
                            result.add(taskTrackingService.enrichedTaskToMap(task));
                        }
                    });

            // Tasks where user is a candidate
            taskService.createTaskQuery()
                    .taskCandidateUser(userId)
                    .active()
                    .list()
                    .forEach(task -> {
                        if (seenTaskIds.add(task.getId())) {
                            result.add(taskTrackingService.enrichedTaskToMap(task));
                        }
                    });

            // Tasks where user's groups are candidates
            for (String groupId : groupIds) {
                taskService.createTaskQuery()
                        .taskCandidateGroup(groupId)
                        .active()
                        .list()
                        .forEach(task -> {
                            if (seenTaskIds.add(task.getId())) {
                                result.add(taskTrackingService.enrichedTaskToMap(task));
                            }
                        });
            }

            // Sort by creation time (newest first)
            result.sort((a, b) -> {
                Date dateA = (Date) a.get("createTime");
                Date dateB = (Date) b.get("createTime");
                if (dateA == null || dateB == null) return 0;
                return dateB.compareTo(dateA);
            });

            log.info("[Get Tasks] Found {} tasks for userId={}", result.size(), userId);
            return ResponseEntity.ok(Response.ok(result));

        } catch (Exception e) {
            log.error("[Get Tasks] Error retrieving tasks for userId={}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Response.error("Failed to retrieve tasks: " + e.getMessage()));
        }
    }

    /**
     * Get a single task by ID with full details
     */
    @GetMapping("/{taskId}")
    public ResponseEntity<Response<Map<String, Object>>> getTaskById(
            @PathVariable("taskId") String taskId,
            @RequestParam("userId") String userId) {
        try {
            Task task = taskService.createTaskQuery()
                    .taskId(taskId)
                    .singleResult();

            if (task == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Response.error("Task not found"));
            }

            // Check access
            if (!taskTrackingService.canUserAccessTask(userId, task)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Response.error("Access denied"));
            }

            Map<String, Object> taskDetails = taskTrackingService.enrichedTaskToMap(task);
            return ResponseEntity.ok(Response.ok(taskDetails));

        } catch (Exception e) {
            log.error("[Get Task] Error retrieving task {}", taskId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Response.error("Failed to retrieve task: " + e.getMessage()));
        }
    }

    /**
     * Start GPS tracking for a specific task
     * This establishes the user->participant mapping for location updates
     */
    @PostMapping("/{taskId}/startTracking")
    public ResponseEntity<Response<Map<String, Object>>> startTrackingForTask(
            @PathVariable("taskId") String taskId,
            @RequestParam("userId") String userId) {
        try {
            Map<String, Object> trackingInfo = taskTrackingService.startTrackingForTask(taskId, userId);

            log.info("[Task Tracking] Started tracking - userId={}, taskId={}, participantId={}",
                    userId, taskId, trackingInfo.get("participantId"));

            return ResponseEntity.ok(Response.ok(trackingInfo));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Response.error(e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Response.error(e.getMessage()));
        } catch (Exception e) {
            log.error("[Task Tracking] Failed to start tracking for task {}", taskId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Response.error("Failed to start tracking: " + e.getMessage()));
        }
    }

    /**
     * Stop GPS tracking for the current task
     */
    @PostMapping("/stopTracking")
    public ResponseEntity<Response<String>> stopTracking(@RequestParam("userId") String userId) {
        try {
            taskTrackingService.stopTracking(userId);
            return ResponseEntity.ok(Response.ok("Tracking stopped"));

        } catch (Exception e) {
            log.error("[Task Tracking] Failed to stop tracking for user {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Response.error("Failed to stop tracking: " + e.getMessage()));
        }
    }

    /**
     * Get current tracking status for a user
     */
    @GetMapping("/trackingStatus")
    public ResponseEntity<Response<Map<String, Object>>> getTrackingStatus(@RequestParam("userId") String userId) {
        try {
            Map<String, Object> status = taskTrackingService.getTrackingStatus(userId);
            return ResponseEntity.ok(Response.ok(status));

        } catch (Exception e) {
            log.error("[Task Tracking] Failed to get tracking status for user {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Response.error("Failed to get tracking status: " + e.getMessage()));
        }
    }

    /**
     * Claim a task for a user
     */
    @PostMapping("/{taskId}/claim")
    public ResponseEntity<Response<String>> claimTask(
            @PathVariable("taskId") String taskId,
            @RequestParam("userId") String userId) {
        try {
            if (taskId == null || taskId.isBlank() || userId == null || userId.isBlank()) {
                return ResponseEntity.badRequest().body(Response.error("taskId and userId are required"));
            }

            Task task = taskService.createTaskQuery()
                    .taskId(taskId)
                    .singleResult();

            if (task == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Response.error("Task not found"));
            }

            // Check if user can claim this task
            if (!taskTrackingService.canUserAccessTask(userId, task)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Response.error("User cannot claim this task"));
            }

            // Check if already assigned
            if (task.getAssignee() != null && !task.getAssignee().equals(userId)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Response.error("Task already assigned to: " + task.getAssignee()));
            }

            taskService.claim(taskId, userId);
            log.info("[Claim Task] Task {} claimed by {}", taskId, userId);

            return ResponseEntity.ok(Response.ok("Task " + taskId + " claimed by " + userId));

        } catch (Exception e) {
            log.error("[Claim Task] Failed to claim taskId={} by userId={}", taskId, userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Response.error("Failed to claim task: " + e.getMessage()));
        }
    }

    /**
     * Release/unclaim a task
     */
    @PostMapping("/{taskId}/unclaim")
    public ResponseEntity<Response<String>> unclaimTask(
            @PathVariable("taskId") String taskId,
            @RequestParam("userId") String userId) {
        try {
            Task task = taskService.createTaskQuery()
                    .taskId(taskId)
                    .singleResult();

            if (task == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Response.error("Task not found"));
            }

            // Check if user is the assignee
            if (!userId.equals(task.getAssignee())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Response.error("Only the assignee can unclaim the task"));
            }

            // In Camunda, unclaim is done by setting assignee to null
            taskService.setAssignee(taskId, null);
            log.info("[Unclaim Task] Task {} unclaimed by {}", taskId, userId);

            return ResponseEntity.ok(Response.ok("Task " + taskId + " unclaimed"));

        } catch (Exception e) {
            log.error("[Unclaim Task] Failed to unclaim taskId={}", taskId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Response.error("Failed to unclaim task: " + e.getMessage()));
        }
    }

    /**
     * Complete a task with optional variables
     */
    @PostMapping("/{taskId}/complete")
    public ResponseEntity<Response<String>> completeTask(
            @PathVariable("taskId") String taskId,
            @RequestParam(value = "userId", required = false) String userId,
            @RequestBody(required = false) Map<String, Map<String, Object>> variables) {
        try {
            if (taskId == null || taskId.isBlank()) {
                return ResponseEntity.badRequest().body(Response.error("taskId is required"));
            }

            Task task = taskService.createTaskQuery()
                    .taskId(taskId)
                    .singleResult();

            if (task == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Response.error("Task not found"));
            }

            // If userId provided, verify they can complete the task
            if (userId != null && !taskTrackingService.canUserAccessTask(userId, task)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Response.error("User cannot complete this task"));
            }

            Map<String, Object> camundaVars = new HashMap<>();

            if (variables != null && variables.containsKey("variables")) {
                for (Map.Entry<String, Object> entry : variables.get("variables").entrySet()) {
                    Map<String, Object> varMap = (Map<String, Object>) entry.getValue();
                    if (varMap != null && varMap.containsKey("value")) {
                        camundaVars.put(entry.getKey(), varMap.get("value"));
                    }
                }
            }

            // If this was a tracked task, clear the tracking
            if (userId != null) {
                taskTrackingService.clearTrackingIfTaskMatches(userId, taskId);
            }

            taskService.complete(taskId, Variables.fromMap(camundaVars));

            log.info("[Complete Task] Task {} completed by {}", taskId, userId != null ? userId : "system");
            return ResponseEntity.ok(Response.ok("Task " + taskId + " completed"));

        } catch (Exception e) {
            log.error("[Complete Task] Failed to complete taskId={}", taskId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Response.error("Failed to complete task: " + e.getMessage()));
        }
    }

    /**
     * Get task variables
     */
    @GetMapping("/{taskId}/variables")
    public ResponseEntity<Response<Map<String, Object>>> getTaskVariables(
            @PathVariable("taskId") String taskId,
            @RequestParam("userId") String userId) {
        try {
            Task task = taskService.createTaskQuery()
                    .taskId(taskId)
                    .singleResult();

            if (task == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Response.error("Task not found"));
            }

            if (!taskTrackingService.canUserAccessTask(userId, task)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Response.error("Access denied"));
            }

            Map<String, Object> variables = taskService.getVariables(taskId);
            return ResponseEntity.ok(Response.ok(variables));

        } catch (Exception e) {
            log.error("[Get Variables] Failed for taskId={}", taskId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Response.error("Failed to get variables: " + e.getMessage()));
        }
    }
}