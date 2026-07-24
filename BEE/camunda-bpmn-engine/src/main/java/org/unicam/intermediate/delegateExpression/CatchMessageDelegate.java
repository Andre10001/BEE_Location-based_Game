package org.unicam.intermediate.delegateExpression;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.Expression;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("catchMessageDelegate")
public class CatchMessageDelegate implements JavaDelegate {

    private Expression getVariableValue;
    private Expression setVariableExpr;

    public void setPlaceVariableExpr(Expression placeVariableExpr) {
        this.getVariableValue = placeVariableExpr;
    }
    public void setDestinationVariableExpr(Expression setVariableExpr) {
        this.setVariableExpr = setVariableExpr;
    }

    @Override
    public void execute(DelegateExecution delegateExecution) throws Exception {
        String varName = (String) getVariableValue.getValue(delegateExecution);
        String setVariableName = (String) setVariableExpr.getValue(delegateExecution);

        // qui le devo settare per usarle poi negli step successivi
        Object varValue = delegateExecution.getVariable(varName);
        delegateExecution.setVariable(setVariableName, varValue);
    }
}
