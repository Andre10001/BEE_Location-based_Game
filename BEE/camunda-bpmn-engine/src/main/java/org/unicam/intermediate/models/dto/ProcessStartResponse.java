package org.unicam.intermediate.models.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ProcessStartResponse {
    private String processInstanceId;
    private String processDefinitionKey;
}
