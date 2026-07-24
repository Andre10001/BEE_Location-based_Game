package org.unicam.intermediate.delegateExpression;

import lombok.Setter;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.ExecutionListener;
import org.camunda.bpm.engine.delegate.Expression;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("throwMessageDelegate")
public class ThrowMessageDelegate implements JavaDelegate {

    private final RuntimeService runtimeService;

    public ThrowMessageDelegate(RuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    @Setter
    private Expression messageNameExpr;
    @Setter
    private Expression setVariableValue;


    @Override
    public void execute(DelegateExecution delegateExecution) throws Exception {
        String messageName = (String) messageNameExpr.getValue(delegateExecution);
        String varValue = (String) setVariableValue.getValue(delegateExecution);
        String correlationKey =  delegateExecution.getBusinessKey();

        Object value = delegateExecution.getVariable(varValue);

        runtimeService.createMessageCorrelation(messageName)
                .processInstanceBusinessKey(correlationKey)
                .setVariable(varValue, value) // Mi serve per passare il valore alla variabile
                .correlate();
    }
}
