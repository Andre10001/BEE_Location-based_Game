package org.unicam.intermediate.service.environmental;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.runtime.EventSubscription;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.unicam.intermediate.events.ParticipantPositionChangedEvent;
import org.unicam.intermediate.service.environmental.binding.BindingTaskRegistry;
import org.unicam.intermediate.service.environmental.movement.MovementTaskRegistry;
import org.unicam.intermediate.service.participant.UserParticipantMappingService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class UnexpectedMovementMonitor {

    private static final String UNEXPECTED_MOVEMENT_SIGNAL = "unexpectedMovement";
    private static final long GRACE_WINDOW_MS = 30_000L;

    private final RuntimeService runtimeService;
    private final MovementTaskRegistry movementTaskRegistry;
    private final BindingTaskRegistry bindingTaskRegistry;
    private final UserParticipantMappingService userParticipantMappingService;

    private final ConcurrentHashMap<String, PendingEntry> pendingEntries = new ConcurrentHashMap<>();

    private record PendingEntry(String participantId, String oldPosition, String newPosition, long detectedAt) {}

    @EventListener
    public void onParticipantPositionChanged(ParticipantPositionChangedEvent event) {
        String participantId = event.participantId();
        String oldPosition = event.oldPosition();
        String newPosition = event.newPosition();

        if (participantId == null || participantId.isBlank()) {
            return;
        }

        // Required behavior: do not trigger when new position is null.
        if (newPosition == null) {
            return;
        }

        // Guard against no-op updates.
        if (Objects.equals(oldPosition, newPosition)) {
            return;
        }

        if (movementTaskRegistry.hasActiveMovementTask(participantId)) {
            log.debug("[UnexpectedMovement] Ignored for participant {}: movement task is active", participantId);
            pendingEntries.remove(participantId);
            return;
        }

        if (bindingTaskRegistry.isBindingInProgressForParticipant(participantId)) {
            log.debug("[UnexpectedMovement] Ignored for participant {}: binding is in progress", participantId);
            pendingEntries.remove(participantId);
            return;
        }

        Set<String> businessKeys = userParticipantMappingService.getBusinessKeysForParticipant(participantId);
        if (businessKeys.isEmpty()) {
            log.debug("[UnexpectedMovement] No business keys mapped for participant {}", participantId);
            return;
        }

        pendingEntries.put(participantId,
                new PendingEntry(participantId, oldPosition, newPosition, System.currentTimeMillis()));
        log.debug("[UnexpectedMovement] Pending entry registered for participant {}, grace window {}ms",
                participantId, GRACE_WINDOW_MS);
    }

    @Scheduled(fixedRate = 1000)
    public void checkPendingEntries() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, PendingEntry> entry : pendingEntries.entrySet()) {
            PendingEntry pending = entry.getValue();
            if (now - pending.detectedAt() < GRACE_WINDOW_MS) {
                continue;
            }
            pendingEntries.remove(entry.getKey());

            // Re-check guards at fire time
            if (movementTaskRegistry.hasActiveMovementTask(pending.participantId())) {
                log.debug("[UnexpectedMovement] Grace window expired but movement task is now active for {}, skipping",
                        pending.participantId());
                continue;
            }
            if (bindingTaskRegistry.isBindingInProgressForParticipant(pending.participantId())) {
                log.debug("[UnexpectedMovement] Grace window expired but binding is now in progress for {}, skipping",
                        pending.participantId());
                continue;
            }

            fireSignal(pending);
        }
    }

    private void fireSignal(PendingEntry pending) {
        Set<String> businessKeys = userParticipantMappingService.getBusinessKeysForParticipant(pending.participantId());
        if (businessKeys.isEmpty()) {
            return;
        }

        Map<String, Object> signalVariables = new HashMap<>();
        signalVariables.put("unexpectedMovement_participantId", pending.participantId());
        signalVariables.put("unexpectedMovement_oldPosition", pending.oldPosition());
        signalVariables.put("unexpectedMovement_newPosition", pending.newPosition());

        int sentSubscriptions = 0;

        for (String businessKey : businessKeys) {
            List<ProcessInstance> instances = runtimeService.createProcessInstanceQuery()
                    .processInstanceBusinessKey(businessKey)
                    .active()
                    .list();

            for (ProcessInstance instance : instances) {
                List<EventSubscription> subscriptions = runtimeService
                        .createEventSubscriptionQuery()
                        .eventType("signal")
                        .eventName(UNEXPECTED_MOVEMENT_SIGNAL)
                        .processInstanceId(instance.getId())
                        .list();

                for (EventSubscription subscription : subscriptions) {
                    runtimeService.signalEventReceived(
                            UNEXPECTED_MOVEMENT_SIGNAL,
                            subscription.getExecutionId(),
                            signalVariables
                    );
                    sentSubscriptions++;
                }
            }
        }

        if (sentSubscriptions > 0) {
            log.warn("[UnexpectedMovement] Sent signal '{}' for participant {} ({} -> {}) to {} subscription(s)",
                    UNEXPECTED_MOVEMENT_SIGNAL,
                    pending.participantId(),
                    pending.oldPosition(),
                    pending.newPosition(),
                    sentSubscriptions);
        } else {
            log.debug("[UnexpectedMovement] No subscriptions found for signal '{}' for participant {}",
                    UNEXPECTED_MOVEMENT_SIGNAL,
                    pending.participantId());
        }
    }
}
