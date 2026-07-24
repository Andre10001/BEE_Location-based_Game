package org.unicam.intermediate.models.record;

public record EnvironmentalTaskInfo(
        String executionId,
        String activityId,
        String guardExpression,
        String action,
        String participantId,
        Double timer
) {
}
