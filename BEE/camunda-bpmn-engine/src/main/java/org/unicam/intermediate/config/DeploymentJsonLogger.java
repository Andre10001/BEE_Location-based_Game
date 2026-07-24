package org.unicam.intermediate.config;

import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.repository.Deployment;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.Participant;
import org.unicam.intermediate.service.environmental.EnvironmentDataService;
import org.unicam.intermediate.service.participant.ParticipantDataService;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Debug utility that logs JSON resources included in newly created Camunda deployments.
 * Useful to verify "Include additional file" payloads sent by the Modeler.
 */
@Component
@Slf4j
public class DeploymentJsonLogger {

    private final RepositoryService repositoryService;
    private final EnvironmentDataService environmentDataService;
    private final ParticipantDataService participantDataService;
    private final Set<String> seenDeploymentIds = ConcurrentHashMap.newKeySet();

    public DeploymentJsonLogger(RepositoryService repositoryService,
                                EnvironmentDataService environmentDataService,
                                ParticipantDataService participantDataService) {
        this.repositoryService = repositoryService;
        this.environmentDataService = environmentDataService;
        this.participantDataService = participantDataService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeSeenDeployments() {
        List<Deployment> existing = repositoryService.createDeploymentQuery()
                .orderByDeploymentTime()
                .asc()
                .list();

        for (Deployment deployment : existing) {
            seenDeploymentIds.add(deployment.getId());
        }

        log.info("[DeploymentJsonLogger] Initialized with {} existing deployments", existing.size());
    }

    @Scheduled(fixedDelay = 2000)
    public void logJsonFilesFromNewDeployments() {
        List<Deployment> latestDeployments = repositoryService.createDeploymentQuery()
                .orderByDeploymentTime()
                .desc()
                .listPage(0, 20);

        if (latestDeployments.isEmpty()) {
            return;
        }

        List<Deployment> newDeployments = new ArrayList<>();
        for (Deployment deployment : latestDeployments) {
            if (seenDeploymentIds.add(deployment.getId())) {
                newDeployments.add(deployment);
            }
        }

        // Log in chronological order when multiple deployments are found together.
        newDeployments.sort(Comparator.comparing(Deployment::getDeploymentTime));

        for (Deployment deployment : newDeployments) {
            processDeploymentResources(deployment);
        }
    }

    private void processDeploymentResources(Deployment deployment) {
        List<String> resourceNames = repositoryService.getDeploymentResourceNames(deployment.getId());

        if (resourceNames == null || resourceNames.isEmpty()) {
            return;
        }

        logJsonResources(deployment, resourceNames);
        logCollaborationParticipants(deployment, resourceNames);
    }

    private void logJsonResources(Deployment deployment, List<String> resourceNames) {

        List<String> jsonResources = resourceNames.stream()
                .filter(name -> name != null && name.toLowerCase().endsWith(".json"))
                .toList();

        if (jsonResources.isEmpty()) {
            return;
        }

        log.info("[DeploymentJsonLogger] New deployment '{}' ({}) has JSON resources: {}",
                deployment.getName(), deployment.getId(), jsonResources);

        for (String resourceName : jsonResources) {
            try (InputStream inputStream = repositoryService.getResourceAsStream(deployment.getId(), resourceName)) {
                if (inputStream == null) {
                    log.warn("[DeploymentJsonLogger] JSON resource '{}' could not be read from deployment {}",
                            resourceName, deployment.getId());
                    continue;
                }

                String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                String source = deployment.getId() + "/" + resourceName;

                boolean environmentApplied = environmentDataService.loadEnvironmentFromJsonContent(content, source);
                int loadedParticipants = participantDataService.loadParticipantsFromJsonContent(content, source);

                if (environmentApplied || loadedParticipants > 0) {
                    log.info("[DeploymentJsonLogger] Applied deployment JSON '{}' (environmentApplied={}, participantsLoaded={})",
                            source, environmentApplied, loadedParticipants);
                } else {
                    log.info("[DeploymentJsonLogger] JSON '{}' does not contain environment/participants sections to apply",
                            source);
                }
            } catch (Exception e) {
                log.error("[DeploymentJsonLogger] Failed reading JSON resource '{}' from deployment {}: {}",
                        resourceName, deployment.getId(), e.getMessage(), e);
            }
        }
    }

    private void logCollaborationParticipants(Deployment deployment, List<String> resourceNames) {
        List<String> bpmnResources = resourceNames.stream()
                .filter(name -> name != null && name.toLowerCase().endsWith(".bpmn"))
                .toList();

        if (bpmnResources.isEmpty()) {
            return;
        }

        List<org.unicam.intermediate.models.pojo.Participant> extractedParticipants = new ArrayList<>();

        for (String resourceName : bpmnResources) {
            try (InputStream inputStream = repositoryService.getResourceAsStream(deployment.getId(), resourceName)) {
                if (inputStream == null) {
                    continue;
                }

                BpmnModelInstance modelInstance = Bpmn.readModelFromStream(inputStream);
                var participants = modelInstance.getModelElementsByType(Participant.class);

                if (participants == null || participants.isEmpty()) {
                    continue;
                }

                log.info("[DeploymentJsonLogger] Collaboration participants in '{}' (deployment={}):",
                        resourceName, deployment.getId());

                for (Participant participant : participants) {
                    String participantId = participant.getId();
                    String participantName = participant.getName();
                    log.info("[DeploymentJsonLogger] - participantId='{}', participantName='{}'",
                            participantId, participantName);

                    if (participantId == null || participantId.isBlank()) {
                        continue;
                    }

                    org.unicam.intermediate.models.pojo.Participant runtimeParticipant =
                            new org.unicam.intermediate.models.pojo.Participant();
                    runtimeParticipant.setId(participantId);
                    runtimeParticipant.setName(participantName);
                    runtimeParticipant.setPosition(null);
                    extractedParticipants.add(runtimeParticipant);
                }
            } catch (Exception e) {
                log.warn("[DeploymentJsonLogger] Failed to parse BPMN '{}' for participants: {}",
                        resourceName, e.getMessage());
            }
        }

        if (!extractedParticipants.isEmpty()) {
            String source = deployment.getId() + "/collaboration-participants";
            int added = participantDataService.mergeParticipants(extractedParticipants, source);
            log.info("[DeploymentJsonLogger] Merged BPMN collaboration participants for deployment {} (added={})",
                    deployment.getId(), added);
        }
    }
}
