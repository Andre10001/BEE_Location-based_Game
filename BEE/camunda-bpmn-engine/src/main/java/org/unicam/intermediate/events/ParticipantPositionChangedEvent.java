package org.unicam.intermediate.events;

/**
 * Published when a participant's position is updated.
 * Consumers can react to update derived attributes (e.g., occupied) on the affected places.
 */
public record ParticipantPositionChangedEvent(
        String participantId,
        String oldPosition,
        String newPosition
) {}
