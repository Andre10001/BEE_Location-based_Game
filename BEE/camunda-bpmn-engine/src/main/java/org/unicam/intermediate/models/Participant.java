package org.unicam.intermediate.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Participant {
    private String id;
    private String role;
    private String displayName;
    private String processDefinitionKey;

    public String getLogDisplayName() {
        return displayName != null ? displayName : (role != null ? role : id);
    }

    @Override
    public String toString() {
        return String.format("%s (%s)", getLogDisplayName(), id);
    }
}