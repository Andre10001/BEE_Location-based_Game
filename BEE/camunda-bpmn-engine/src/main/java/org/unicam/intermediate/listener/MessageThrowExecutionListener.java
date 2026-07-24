package org.unicam.intermediate.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.ExecutionListener;
import org.camunda.bpm.engine.runtime.Execution;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.EndEvent;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.IntermediateThrowEvent;
import org.camunda.bpm.model.bpmn.instance.Message;
import org.camunda.bpm.model.bpmn.instance.MessageEventDefinition;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component("messageThrowExecutionListener")
@RequiredArgsConstructor
@Slf4j
public class MessageThrowExecutionListener implements ExecutionListener {

    private final RuntimeService runtimeService;
    private final RepositoryService repositoryService;

    @Override
    public void notify(DelegateExecution execution) {
        String messageName = resolveMessageName(execution);
        if (messageName == null || messageName.isBlank()) {
            return;
        }

        String businessKey = execution.getBusinessKey();
        Map<String, Object> variables = execution.getVariables();

        if (deliverToWaitingReceiver(messageName, businessKey, variables, execution.getCurrentActivityId())) {
            return;
        }

        try {
            if (hasMessageStartDefinition(messageName)) {
                if (businessKey == null || businessKey.isBlank()) {
                    runtimeService.startProcessInstanceByMessage(messageName, variables);
                } else {
                    runtimeService.startProcessInstanceByMessage(messageName, businessKey, variables);
                }
                log.info("[MessageThrowExecutionListener] Started process by message '{}' from '{}' (businessKey={})",
                        messageName, execution.getCurrentActivityId(), businessKey);
            } else {
                log.warn("[MessageThrowExecutionListener] No receiver and no message start for '{}' from '{}' (businessKey={})",
                        messageName, execution.getCurrentActivityId(), businessKey);
            }
        } catch (ProcessEngineException ex) {
            log.warn("[MessageThrowExecutionListener] Start-by-message failed for '{}' from '{}' (businessKey={}): {}",
                    messageName, execution.getCurrentActivityId(), businessKey, ex.getMessage());
        }
    }

    private boolean deliverToWaitingReceiver(String messageName,
                                             String businessKey,
                                             Map<String, Object> variables,
                                             String sourceActivityId) {
        Execution receiver = findReceiver(messageName, businessKey);
        if (receiver == null) {
            // Fallback: if strict business-key match fails, try any waiting receiver for this message.
            receiver = findReceiver(messageName, null);
        }

        if (receiver == null) {
            return false;
        }

        try {
            runtimeService.messageEventReceived(messageName, receiver.getId(), variables);
            log.info("[MessageThrowExecutionListener] Delivered '{}' from '{}' to execution '{}' (businessKey={})",
                    messageName, sourceActivityId, receiver.getId(), businessKey);
            return true;
        } catch (ProcessEngineException ex) {
            log.warn("[MessageThrowExecutionListener] Delivery failed for '{}' from '{}' to execution '{}' (businessKey={}): {}",
                    messageName, sourceActivityId, receiver.getId(), businessKey, ex.getMessage());
            return false;
        }
    }

    private Execution findReceiver(String messageName, String businessKey) {
        var query = runtimeService.createExecutionQuery()
                .messageEventSubscriptionName(messageName)
                .active();

        if (businessKey != null && !businessKey.isBlank()) {
            query = query.processInstanceBusinessKey(businessKey);
        }

        return query.listPage(0, 1).stream().findFirst().orElse(null);
    }

    private boolean hasMessageStartDefinition(String messageName) {
        return repositoryService.createProcessDefinitionQuery()
                .messageEventSubscriptionName(messageName)
                .latestVersion()
                .active()
                .count() > 0;
    }

    private String resolveMessageName(DelegateExecution execution) {
        BpmnModelInstance modelInstance = repositoryService.getBpmnModelInstance(execution.getProcessDefinitionId());
        if (modelInstance == null) {
            return null;
        }

        FlowNode flowNode = modelInstance.getModelElementById(execution.getCurrentActivityId());
        if (flowNode instanceof IntermediateThrowEvent throwEvent) {
            return getMessageName(throwEvent.getEventDefinitions().stream()
                    .filter(MessageEventDefinition.class::isInstance)
                    .map(MessageEventDefinition.class::cast)
                    .findFirst()
                    .orElse(null));
        }

        if (flowNode instanceof EndEvent endEvent) {
            return getMessageName(endEvent.getEventDefinitions().stream()
                    .filter(MessageEventDefinition.class::isInstance)
                    .map(MessageEventDefinition.class::cast)
                    .findFirst()
                    .orElse(null));
        }

        return null;
    }

    private String getMessageName(MessageEventDefinition messageEventDefinition) {
        if (messageEventDefinition == null) {
            return null;
        }

        Message message = messageEventDefinition.getMessage();
        if (message == null) {
            return null;
        }

        String messageName = message.getName();
        if (messageName == null || messageName.isBlank()) {
            return message.getId();
        }

        return messageName;
    }
}
