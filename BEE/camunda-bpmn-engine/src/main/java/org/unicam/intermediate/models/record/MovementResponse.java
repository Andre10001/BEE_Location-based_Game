package org.unicam.intermediate.models.record;

public record MovementResponse(boolean success, String message, String destination, String userId, String processId) {

    public static MovementResponse noActiveTasks(String userId) {
        return new MovementResponse(
                false,
                "No active movement tasks",
                null,
                userId,
                null
        );
    }

    public static MovementResponse notInTargetArea(String userId) {
        return new MovementResponse(
                false,
                "Device is NOT inside any target area",
                null,
                userId,
                null
        );
    }

    public static MovementResponse enteredArea(String userId, String destination, String processId) {
        return new MovementResponse(
                true,
                "Device entered target area: " + destination,
                destination,
                userId,
                processId
        );
    }
}