package org.unicam.intermediate.models.record;

public record BindingTaskInfo(
        String businessKey,
        String participantId,
        String targetParticipantId,
        String executionId,
        String activityId,
        Double timer
) {

        public BindingTaskInfo(String businessKey,
                                                   String participantId,
                                                   String targetParticipantId,
                                                   String executionId) {
                this(businessKey, participantId, targetParticipantId, executionId, null, null);
        }
}
