package org.unicam.intermediate.service.environmental;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.unicam.intermediate.models.WaitingBinding;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
public class BindingService {
    
    // Chiave: businessKey:participantId -> WaitingBinding
    private final Map<String, WaitingBinding> waitingBindings = new ConcurrentHashMap<>();
    private final Map<String, WaitingBinding> waitingUnbindings = new ConcurrentHashMap<>();

    public synchronized Optional<WaitingBinding> findWaitingBinding(String businessKey, String currentParticipantId) {
        String checkingKey = businessKey + ":" + currentParticipantId;
        WaitingBinding waiting = waitingBindings.get(checkingKey);
        
        if (waiting != null) {
            log.info("[BindingService] Found waiting binding for key: {}", checkingKey);
            return Optional.of(waiting);
        }
        
        log.debug("[BindingService] No waiting binding found for key: {}", checkingKey);
        return Optional.empty();
    }

    public synchronized void addWaitingBinding(WaitingBinding binding) {
        String waitingKey = binding.getWaitingKey();
        waitingBindings.put(waitingKey, binding);
        log.info("[BindingService] Added waiting binding: {} waiting for {}", 
                binding.getCurrentParticipantId(), binding.getTargetParticipantId());
    }

    public synchronized void removeWaitingBinding(String businessKey, String participantId) {
        String key = businessKey + ":" + participantId;
        WaitingBinding removed = waitingBindings.remove(key);
        if (removed != null) {
            log.info("[BindingService] Removed waiting binding for key: {}", key);
        }
    }

    public synchronized Optional<WaitingBinding> findWaitingUnbinding(String businessKey, String currentParticipantId) {
        String checkingKey = businessKey + ":" + currentParticipantId;
        WaitingBinding waiting = waitingUnbindings.get(checkingKey);
        
        if (waiting != null) {
            log.info("[BindingService] Found waiting unbinding for key: {}", checkingKey);
            return Optional.of(waiting);
        }
        
        log.debug("[BindingService] No waiting unbinding found for key: {}", checkingKey);
        return Optional.empty();
    }

    public synchronized void addWaitingUnbinding(WaitingBinding unbinding) {
        String waitingKey = unbinding.getWaitingKey();
        waitingUnbindings.put(waitingKey, unbinding);
        log.info("[BindingService] Added waiting unbinding: {} waiting for {}", 
                unbinding.getCurrentParticipantId(), unbinding.getTargetParticipantId());
    }

    public synchronized void removeWaitingUnbinding(String businessKey, String participantId) {
        String key = businessKey + ":" + participantId;
        WaitingBinding removed = waitingUnbindings.remove(key);
        if (removed != null) {
            log.info("[BindingService] Removed waiting unbinding for key: {}", key);
        }
    }

    public List<WaitingBinding> getAllWaitingBindings() {
        return waitingBindings.values().stream().collect(Collectors.toList());
    }

    public List<WaitingBinding> getAllWaitingUnbindings() {
        return waitingUnbindings.values().stream().collect(Collectors.toList());
    }

    public void clearAll() {
        waitingBindings.clear();
        waitingUnbindings.clear();
        log.info("[BindingService] Cleared all waiting bindings and unbindings");
    }
}