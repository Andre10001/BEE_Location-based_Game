// src/main/java/org/unicam/intermediate/models/dto/ProcessStartRequest.java
package org.unicam.intermediate.models.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.Map;

@Data
public class ProcessStartRequest {
    
    @NotBlank(message = "Process ID is required")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Process ID contains invalid characters")
    private String processId;
    
    @NotBlank(message = "User ID is required")
    @Size(min = 1, max = 100, message = "User ID must be between 1 and 100 characters")
    private String userId;
    
    private Map<String, Object> variables;
    
    private String businessKey;
}