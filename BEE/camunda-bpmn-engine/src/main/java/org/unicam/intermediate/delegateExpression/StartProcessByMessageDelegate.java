package org.unicam.intermediate.delegateExpression;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.Expression;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.engine.impl.cfg.TransactionState;
import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("startProcessByMessageDelegate")
@Slf4j
@RequiredArgsConstructor
public class StartProcessByMessageDelegate implements JavaDelegate {

    private final RuntimeService runtimeService;
    private final RepositoryService repositoryService;

    @Setter
    private Expression messageNameExpr;

    @Setter
    private Expression businessKeyExpr;

    @Override
    public void execute(DelegateExecution execution) {
        String messageName = getStringValue(messageNameExpr, execution);
        if (messageName == null || messageName.isBlank()) {
            throw new BpmnError("StartProcessByMessageError", "Missing messageName for message start");
        }

        String businessKey = getStringValue(businessKeyExpr, execution);
        if (businessKey == null || businessKey.isBlank()) {
            businessKey = execution.getBusinessKey();
        }

        final String resolvedMessageName = messageName;
        final String resolvedBusinessKey = businessKey;

        var commandContext = Context.getCommandContext();
        if (commandContext != null && commandContext.getTransactionContext() != null) {
            commandContext.getTransactionContext().addTransactionListener(
                    TransactionState.COMMITTED,
                    command -> startProcessByMessage(resolvedMessageName, resolvedBusinessKey)
            );

            log.info("[StartByMessage] Scheduled process start by message '{}' after commit (businessKey='{}')",
                    resolvedMessageName, resolvedBusinessKey);
            return;
        }

        startProcessByMessage(resolvedMessageName, resolvedBusinessKey);
    }

    private void startProcessByMessage(String messageName, String businessKey) {
        if (businessKey == null || businessKey.isBlank()) {
            runtimeService.startProcessInstanceByMessage(messageName);
            log.info("[StartByMessage] Started process by message '{}' without businessKey", messageName);
            return;
        }

        if (hasActiveMessageStartedProcess(messageName, businessKey)) {
            log.info("[StartByMessage] Skipped message '{}' because a target process is already active with businessKey '{}'",
                    messageName, businessKey);
            return;
        }

        runtimeService.startProcessInstanceByMessage(messageName, businessKey);
        log.info("[StartByMessage] Started process by message '{}' with businessKey '{}'",
                messageName, businessKey);
    }

    private boolean hasActiveMessageStartedProcess(String messageName, String businessKey) {
        List<ProcessDefinition> targetDefinitions = repositoryService.createProcessDefinitionQuery()
                .messageEventSubscriptionName(messageName)
                .latestVersion()
                .active()
                .list();

        for (ProcessDefinition definition : targetDefinitions) {
            long activeInstances = runtimeService.createProcessInstanceQuery()
                    .processDefinitionKey(definition.getKey())
                    .processInstanceBusinessKey(businessKey)
                    .active()
                    .count();

            if (activeInstances > 0) {
                return true;
            }
        }

        return false;
    }

    private String getStringValue(Expression expression, DelegateExecution execution) {
        if (expression == null) {
            return null;
        }

        Object value = expression.getValue(execution);
        return value != null ? value.toString() : null;
    }
}
