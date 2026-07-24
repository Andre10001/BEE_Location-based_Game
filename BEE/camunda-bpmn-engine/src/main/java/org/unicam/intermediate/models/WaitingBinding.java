package org.unicam.intermediate.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.unicam.intermediate.models.enums.TaskType;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WaitingBinding {
    private String processDefinitionKey;
    private String targetParticipantId;
    private String currentParticipantId;
    private String businessKey;
    private String executionId;
    private TaskType taskType;
    private Instant createdAt;
    private String requiredPlace; // Place dove deve avvenire il binding/unbinding

    public WaitingBinding(String processDefinitionKey, String targetParticipantId,
                          String currentParticipantId, String businessKey,
                          String executionId, TaskType taskType, Instant createdAt) {
        this(processDefinitionKey, targetParticipantId, currentParticipantId,
                businessKey, executionId, taskType, createdAt, null);
    }

    public String getWaitingKey() {
        return businessKey + ":" + targetParticipantId;
    }

    public String getLookupKey() {
        return businessKey + ":" + currentParticipantId;
    }

    @Override
    public String toString() {
        return String.format("%s waiting%s: %s ↔ %s (key: %s, created: %s)",
                taskType != null ? taskType.toString() : "Unknown",
                requiredPlace != null ? " in place " + requiredPlace : "",
                currentParticipantId,
                targetParticipantId,
                businessKey,
                createdAt);
    }
}