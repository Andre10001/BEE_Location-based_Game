// src/main/java/org/unicam/intermediate/models/dto/GpsRequest.java
package org.unicam.intermediate.models.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class GpsRequest {
    
    @NotBlank(message = "User ID is required")
    @Size(min = 1, max = 100, message = "User ID must be between 1 and 100 characters")
    private String userId;
    
    @NotNull(message = "Latitude is required")
    @DecimalMin(value = "-90.0", message = "Latitude must be >= -90")
    @DecimalMax(value = "90.0", message = "Latitude must be <= 90")
    private Double lat;
    
    @NotNull(message = "Longitude is required")
    @DecimalMin(value = "-180.0", message = "Longitude must be >= -180")
    @DecimalMax(value = "180.0", message = "Longitude must be <= 180")
    private Double lon;
}