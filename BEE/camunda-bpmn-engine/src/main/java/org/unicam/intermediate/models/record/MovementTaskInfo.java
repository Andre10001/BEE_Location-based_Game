package org.unicam.intermediate.models.record;

public record MovementTaskInfo(
        String executionId,
        String destination,
        String participantId,
        String activityId,
        Double timer
) {
}
