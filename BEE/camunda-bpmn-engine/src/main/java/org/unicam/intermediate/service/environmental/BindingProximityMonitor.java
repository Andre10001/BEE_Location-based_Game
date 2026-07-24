// src/main/java/org/unicam/intermediate/service/environmental/BindingProximityMonitor.java

package org.unicam.intermediate.service.environmental;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.RuntimeService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.unicam.intermediate.models.WaitingBinding;
import org.unicam.intermediate.models.pojo.PhysicalPlace;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@AllArgsConstructor
public class BindingProximityMonitor {
    
    private final BindingService bindingService;
    private final ProximityService proximityService;
    private final RuntimeService runtimeService;
    
    /**
     * Check every 5 seconds if waiting participants are now in the same place
     */
    @Scheduled(fixedDelay = 5000)
    public void checkWaitingBindings() {
        // Check bindings
        processWaitingList(bindingService.getAllWaitingBindings(), true);
        
        // Check unbindings
        processWaitingList(bindingService.getAllWaitingUnbindings(), false);
    }
    
    private void processWaitingList(List<WaitingBinding> waitingList, boolean isBinding) {
        String operation = isBinding ? "BINDING" : "UNBINDING";
        
        // Group by business key
        Map<String, List<WaitingBinding>> byBusinessKey = waitingList.stream()
                .collect(Collectors.groupingBy(WaitingBinding::getBusinessKey));
        
        for (List<WaitingBinding> bindings : byBusinessKey.values()) {
            if (bindings.size() >= 2) {
                // Check pairs
                for (int i = 0; i < bindings.size() - 1; i++) {
                    for (int j = i + 1; j < bindings.size(); j++) {
                        checkAndSignalPair(bindings.get(i), bindings.get(j), isBinding, operation);
                    }
                }
            }
        }
    }
    
    private void checkAndSignalPair(WaitingBinding binding1, WaitingBinding binding2, 
                                   boolean isBinding, String operation) {
        // Check if they're waiting for each other
        if (!binding1.getTargetParticipantId().equals(binding2.getCurrentParticipantId()) ||
            !binding2.getTargetParticipantId().equals(binding1.getCurrentParticipantId())) {
            return;
        }
        
        // Check if in same place
        PhysicalPlace place = proximityService.getBindingPlace(
                binding1.getCurrentParticipantId(),
                binding2.getCurrentParticipantId());
        
        if (place != null) {
            log.info("[Monitor] {} - Participants now in same place: {} ({}). Signaling processes!", 
                    operation, place.getId(), place.getName());
            
            try {
                // Remove from waiting
                if (isBinding) {
                    bindingService.removeWaitingBinding(binding1.getBusinessKey(), binding1.getTargetParticipantId());
                    bindingService.removeWaitingBinding(binding2.getBusinessKey(), binding2.getTargetParticipantId());
                } else {
                    bindingService.removeWaitingUnbinding(binding1.getBusinessKey(), binding1.getTargetParticipantId());
                    bindingService.removeWaitingUnbinding(binding2.getBusinessKey(), binding2.getTargetParticipantId());
                }
                
                // Signal both
                runtimeService.signal(binding1.getExecutionId());
                runtimeService.signal(binding2.getExecutionId());
                
                log.info("[Monitor] {} completed successfully in place: {}", operation, place.getName());
                
            } catch (Exception e) {
                log.error("[Monitor] Failed to signal {} executions", operation, e);
            }
        }
    }
}