package org.unicam.intermediate.utils.BooleanEvaluation;

public class BooleanExpressionEvaluator {
    public static boolean evaluate(double left, String operator, double right) {
        return switch (operator) {
            case ">"  -> left > right;
            case "<"  -> left < right;
            case ">=" -> left >= right;
            case "<=" -> left <= right;
            case "==" -> left == right;
            case "!=" -> left != right;
            default    -> throw new IllegalArgumentException("Operator not found: " + operator);
        };
    }
}



