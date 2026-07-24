package org.unicam.intermediate.delegateExpression;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;
import org.unicam.intermediate.service.environmental.EnvironmentDataService;

@Component("loadEnvironmentDelegate")
@Slf4j
@AllArgsConstructor
public class LoadEnvironmentDelegate implements JavaDelegate {

    private final EnvironmentDataService environmentDataService;

    @Override
    public void execute(DelegateExecution execution) {
        try {
            environmentDataService.reloadEnvironment();
        } catch (Exception e) {
            log.error("[LoadEnvironmentDelegate] Failed to reload environment: {}", e.getMessage());
            throw new BpmnError("LoadEnvironmentError", "Failed to reload environment");
        }
    }
}
