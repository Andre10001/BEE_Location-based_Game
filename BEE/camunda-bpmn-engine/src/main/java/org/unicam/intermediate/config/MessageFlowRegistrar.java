package org.unicam.intermediate.config;

import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.unicam.intermediate.service.MessageFlowRegistry;

import java.util.List;

/**
 * Registers message flows after the application is fully initialized
 * to avoid circular dependency issues
 */
@Component
@Slf4j
public class MessageFlowRegistrar {

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private MessageFlowRegistry messageFlowRegistry;

    @EventListener(ApplicationReadyEvent.class)
    public void registerMessageFlows() {
        log.info("[MessageFlowRegistrar] Application ready - registering message flows for all deployed processes");
        
        List<ProcessDefinition> definitions = repositoryService.createProcessDefinitionQuery()
                .latestVersion()
                .list();
        
        for (ProcessDefinition definition : definitions) {
            try {
                BpmnModelInstance model = repositoryService.getBpmnModelInstance(definition.getId());
                messageFlowRegistry.registerFlowsForProcess(definition.getId(), model);
                log.info("[MessageFlowRegistrar] Registered message flows for process: {}", definition.getKey());
            } catch (Exception e) {
                log.error("[MessageFlowRegistrar] Failed to register flows for process {}: {}", 
                        definition.getKey(), e.getMessage());
            }
        }
    }
}