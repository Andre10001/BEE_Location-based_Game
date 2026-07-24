// src/main/java/org/unicam/intermediate/config/EnvironmentInitializer.java
package org.unicam.intermediate.config;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.unicam.intermediate.service.environmental.EnvironmentDataService;

@Component
@Order(1)
@Slf4j
@AllArgsConstructor
public class EnvironmentInitializer implements ApplicationRunner {
    
    private final EnvironmentDataService environmentDataService;
    
    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("[EnvironmentInitializer] Initializing Environment on startup");
        
        try {
            environmentDataService.loadEnvironmentData();
            
            if (environmentDataService.isLoaded()) {
                log.info("[EnvironmentInitializer] Environment loaded with {} physical places",
                        environmentDataService.getPhysicalPlaces().size());
            } else {
                log.warn("[EnvironmentInitializer] Environment is empty - deploy a process with environment.json");
            }
        } catch (Exception e) {
            log.error("[EnvironmentInitializer]Failed to load environment", e);
        }
    }
}