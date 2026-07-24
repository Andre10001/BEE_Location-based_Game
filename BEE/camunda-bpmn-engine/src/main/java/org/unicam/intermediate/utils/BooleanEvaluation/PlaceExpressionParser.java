package org.unicam.intermediate.utils.BooleanEvaluation;

public class PlaceExpressionParser {

    public static class ConditionParts {
        public final String placeId;
        public final String attribute;
        public final String operator;
        public final String value;

        public ConditionParts(String placeId, String attribute, String operator, String value) {
            this.placeId = placeId;
            this.attribute = attribute;
            this.operator = operator;
            this.value = value;
        }
    }

    /**
     * Parse a condition
     * input example: "place1.temperature > 0"
     */
    public static ConditionParts parse(String expressionText) {
        // split
        String[] parts = expressionText.split("\s*(>=|<=|==|>|<|!=)\s*");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Formato condizione non valido: " + expressionText);
        }
        // get evaluation operator
        String operator = expressionText.substring(parts[0].length(), expressionText.length() - parts[1].length()).trim();
        // get placeId.attribute
        String[] left = parts[0].split("\\.");
        if (left.length != 2) {
            throw new IllegalArgumentException("PlacePart not valid " + parts[0]);
        }
        String placeId = left[0];
        String attribute = left[1];
        String value = parts[1].trim();
        return new ConditionParts(placeId, attribute, operator, value);
    }
}