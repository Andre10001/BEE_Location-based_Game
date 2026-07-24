// src/main/java/org/unicam/intermediate/service/participant/UserParticipantMappingService.java

package org.unicam.intermediate.service.participant;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.Participant;
import org.springframework.stereotype.Service;
import org.unicam.intermediate.service.TaskAuthorizationService;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@AllArgsConstructor
public class UserParticipantMappingService {

        private final RuntimeService runtimeService;
        private final RepositoryService repositoryService;
        private final TaskService taskService;
        private final IdentityService identityService;
        private final ParticipantService participantService;
        private final TaskAuthorizationService taskAuthorizationService;

        // businessKey -> userId -> participantId
        private final Map<String, Map<String, String>> mappings = new ConcurrentHashMap<>();

        // userId -> active tracking context
        private final Map<String, UserParticipantMappingService.TrackingContext> activeTracking = new ConcurrentHashMap<>();

        /**
         * Auto-discover and register the appropriate participant for a user
         */
        public ParticipantDiscoveryResult autoDiscoverAndRegister(String userId, String businessKey) {
            try {
                // Check if already mapped
                String existingParticipantId = getParticipantIdForUser(businessKey, userId);
                if (existingParticipantId != null) {
                    String name = getParticipantNameFromProcess(businessKey, existingParticipantId);
                    return new ParticipantDiscoveryResult(existingParticipantId, name, false);
                }

                // Find active process instances
                List<ProcessInstance> instances = runtimeService.createProcessInstanceQuery()
                        .processInstanceBusinessKey(businessKey)
                        .active()
                        .list();

                if (instances.isEmpty()) {
                    log.debug("[UserMapping] No active processes for businessKey: {}", businessKey);
                    return null;
                }

                // Collect participant states
                Map<String, ParticipantInfo> participantStates = analyzeParticipants(instances);

                // Find best match for user
                ParticipantDiscoveryResult result = findBestParticipantForUser(
                        userId, businessKey, participantStates);

                if (result != null) {
                    // Register the mapping
                    registerUserAsParticipant(businessKey, userId, result.participantId);
                    log.info("[UserMapping] Auto-registered user {} as participant {} for BK {}",
                            userId, result.participantId, businessKey);
                    result.newlyRegistered = true;
                }

                return result;

            } catch (Exception e) {
                log.error("[UserMapping] Error during auto-discovery for user {}", userId, e);
                return null;
            }
        }

        private Map<String, ParticipantInfo> analyzeParticipants(List<ProcessInstance> instances) {
            Map<String, ParticipantInfo> participantStates = new HashMap<>();

            for (ProcessInstance pi : instances) {
                BpmnModelInstance model = repositoryService.getBpmnModelInstance(pi.getProcessDefinitionId());
                Collection<Participant> participants = model.getModelElementsByType(Participant.class);

                for (Participant p : participants) {
                    if (p.getProcess() != null) {
                        String pId = p.getId();
                        String pName = p.getName() != null ? p.getName() : pId;

                        ParticipantInfo info = participantStates.computeIfAbsent(pId,
                                k -> new ParticipantInfo(pId, pName));

                        // Find active tasks for this participant
                        List<Task> activeTasks = findTasksForParticipant(pi.getId(), pId);
                        info.activeTasks.addAll(activeTasks);

                        // Check if already claimed
                        String existingUser = getUserForParticipant(pi.getBusinessKey(), pId);
                        if (existingUser != null) {
                            info.claimedByUser = existingUser;
                        }
                    }
                }
            }

            return participantStates;
        }

        private List<Task> findTasksForParticipant(String processInstanceId, String participantId) {
            List<Task> result = new ArrayList<>();

            List<Task> allTasks = taskService.createTaskQuery()
                    .processInstanceId(processInstanceId)
                    .active()
                    .list();

            for (Task task : allTasks) {
                String taskParticipantId = participantService.resolveParticipantForTask(task);
                if (participantId.equals(taskParticipantId)) {
                    result.add(task);
                }
            }

            return result;
        }

        private ParticipantDiscoveryResult findBestParticipantForUser(
                String userId, String businessKey, Map<String, ParticipantInfo> participantStates) {

            // 1. Priority: Tasks assigned to this user
            for (ParticipantInfo info : participantStates.values()) {
                for (Task task : info.activeTasks) {
                    if (userId.equals(task.getAssignee())) {
                        log.info("[UserMapping] Found participant {} with task assigned to user {}",
                                info.participantId, userId);
                        return new ParticipantDiscoveryResult(info.participantId, info.participantName, false);
                    }
                }
            }

            // 2. Unclaimed participant with accessible tasks
            for (ParticipantInfo info : participantStates.values()) {
                if (info.claimedByUser == null) {
                    for (Task task : info.activeTasks) {
                        if (taskAuthorizationService.canUserAccessTask(userId, task)) {
                            log.info("[UserMapping] Found unclaimed participant {} accessible to user {}",
                                    info.participantId, userId);
                            return new ParticipantDiscoveryResult(info.participantId, info.participantName, false);
                        }
                    }
                }
            }

            // 3. Match based on user groups
            List<Group> userGroups = identityService.createGroupQuery()
                    .groupMember(userId)
                    .list();

            for (ParticipantInfo info : participantStates.values()) {
                if (info.claimedByUser == null) {
                    for (Group group : userGroups) {
                        String groupId = group.getId().toLowerCase();
                        String pName = info.participantName.toLowerCase();

                        if (pName.contains(groupId) || groupId.contains(pName)) {
                            log.info("[UserMapping] Matched participant {} to user {} via group {}",
                                    info.participantId, userId, group.getId());
                            return new ParticipantDiscoveryResult(info.participantId, info.participantName, false);
                        }
                    }
                }
            }

            // 4. First available unclaimed
            for (ParticipantInfo info : participantStates.values()) {
                if (info.claimedByUser == null && !info.activeTasks.isEmpty()) {
                    log.info("[UserMapping] Assigning first available participant {} to user {}",
                            info.participantId, userId);
                    return new ParticipantDiscoveryResult(info.participantId, info.participantName, false);
                }
            }

            return null;
        }

        /**
         * Get user that claimed a participant
         */
        public String getUserForParticipant(String businessKey, String participantId) {
            Map<String, String> businessKeyMappings = mappings.get(businessKey);
            if (businessKeyMappings != null) {
                for (Map.Entry<String, String> entry : businessKeyMappings.entrySet()) {
                    if (participantId.equals(entry.getValue())) {
                        return entry.getKey();
                    }
                }
            }
            return null;
        }

        private String getParticipantNameFromProcess(String businessKey, String participantId) {
            try {
                List<ProcessInstance> instances = runtimeService.createProcessInstanceQuery()
                        .processInstanceBusinessKey(businessKey)
                        .active()
                        .list();

                if (!instances.isEmpty()) {
                    return participantService.getParticipantName(
                            instances.get(0).getProcessDefinitionId(), participantId);
                }
            } catch (Exception e) {
                log.debug("[UserMapping] Could not get participant name", e);
            }
            return participantId;
        }

        // Inner classes
        @Data
        @AllArgsConstructor
        public static class ParticipantDiscoveryResult {
            private String participantId;
            private String participantName;
            private boolean newlyRegistered;
        }

        private static class ParticipantInfo {
            String participantId;
            String participantName;
            List<Task> activeTasks = new ArrayList<>();
            String claimedByUser = null;

            ParticipantInfo(String participantId, String participantName) {
                this.participantId = participantId;
                this.participantName = participantName;
            }
        }

        /**
         * Registra quale participant un user sta impersonando per un dato businessKey
         */
        public void registerUserAsParticipant(String businessKey, String userId, String participantId) {
            mappings.computeIfAbsent(businessKey, k -> new ConcurrentHashMap<>())
                    .put(userId, participantId);

            log.info("[UserMapping] Registered user {} as participant {} for businessKey {}",
                    userId, participantId, businessKey);
        }

        /**
         * Set active tracking for a user (quando seleziona un task)
         */
        public void setActiveTracking(String userId, String taskId, String processInstanceId,
                                      String businessKey, String participantId, String participantName) {
            TrackingContext context = new TrackingContext(
                    taskId, processInstanceId, businessKey, participantId, participantName, Instant.now()
            );
            activeTracking.put(userId, context);

            // Also register the mapping
            if (businessKey != null && participantId != null) {
                registerUserAsParticipant(businessKey, userId, participantId);
            }

            log.info("[UserMapping] Set active tracking for user {} -> participant {}", userId, participantId);
        }

        /**
         * Get active tracking context for a user
         */
        public TrackingContext getActiveTracking(String userId) {
            return activeTracking.get(userId);
        }

        /**
         * Clear tracking for a user
         */
        public void clearTracking(String userId) {
            activeTracking.remove(userId);
        }

        /**
         * Ottieni il participantId per un user in un dato businessKey
         */
        public String getParticipantIdForUser(String businessKey, String userId) {
            // Prima controlla il tracking attivo
            TrackingContext tracking = activeTracking.get(userId);
            if (tracking != null && tracking.getBusinessKey().equals(businessKey)) {
                return tracking.getParticipantId();
            }

            // Poi controlla i mapping salvati
            Map<String, String> businessKeyMappings = mappings.get(businessKey);
            if (businessKeyMappings != null) {
                return businessKeyMappings.get(userId);
            }

            return null;
        }

        public Set<String> getBusinessKeysForParticipant(String participantId) {
            Set<String> result = new HashSet<>();
            if (participantId == null || participantId.isBlank()) {
                return result;
            }

            for (Map.Entry<String, Map<String, String>> entry : mappings.entrySet()) {
                if (entry.getValue().containsValue(participantId)) {
                    result.add(entry.getKey());
                }
            }

            for (TrackingContext context : activeTracking.values()) {
                if (participantId.equals(context.getParticipantId())
                        && context.getBusinessKey() != null
                        && !context.getBusinessKey().isBlank()) {
                    result.add(context.getBusinessKey());
                }
            }

            return result;
        }

        @Data
        @AllArgsConstructor
        public static class TrackingContext {
            private String taskId;
            private String processInstanceId;
            private String businessKey;
            private String participantId;
            private String participantName;
            private Instant startedAt;
        }

}