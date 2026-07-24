package org.unicam.intermediate.service.environmental;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.runtime.EventSubscription;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.unicam.intermediate.events.ParticipantPositionChangedEvent;
import org.unicam.intermediate.service.participant.ParticipantDataService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Monitors bound participant pairs for discordant positions.
 *
 * When two participants complete a binding task and are now "bound" together,
 * this monitor tracks them. If they diverge in position, a 30-second grace
 * window is applied. If they remain discordant after 30 seconds, a
 * "discordantPositions" signal is emitted to the process instance,
 * allowing event sub-processes with signal start to handle the situation.
 */
@Service
@Slf4j
@AllArgsConstructor
public class BoundParticipantsDiscordanceMonitor {

    private final ParticipantDataService participantDataService;
    private final RuntimeService runtimeService;

    /**
     * Internal record for tracking bound pair state
     */
    private static class BoundPairState {
        final String businessKey;
        final String processInstanceId;
        final String participantA;
        final String participantB;
        long discordanceStartTime; // When the divergence was first detected
        boolean hasRaisedError; // Flag to prevent repeated error raises

        BoundPairState(String businessKey, String processInstanceId, String participantA, String participantB) {
            this.businessKey = businessKey;
            this.processInstanceId = processInstanceId;
            this.participantA = participantA;
            this.participantB = participantB;
            this.discordanceStartTime = -1;
            this.hasRaisedError = false;
        }

        String getPairKey() {
            String scopeId = processInstanceId != null ? processInstanceId : businessKey;
            return scopeId + ":" + participantA + "<->" + participantB;
        }
    }

    // Key: businessKey:participantA<->participantB -> BoundPairState
    private final Map<String, BoundPairState> boundPairs = new ConcurrentHashMap<>();

    private static final int DISCORDANCE_GRACE_WINDOW_MS = 30000; // 30 seconds
    private static final String DISCORDANT_POSITIONS_SIGNAL = "discordantPositions";

    /**
     * Register a pair of participants as bound.
     * This is called when a binding task completes successfully.
     */
    public void markBound(String businessKey, String participantA, String participantB) {
        markBound(businessKey, null, participantA, participantB);
        }

        public void markBound(String businessKey, String processInstanceId, String participantA, String participantB) {
        if (businessKey == null || participantA == null || participantB == null) {
            log.warn("[DiscordanceMonitor] Cannot mark bound pair due to null values: BK={}, PI={}, pA={}, pB={}",
                businessKey, processInstanceId, participantA, participantB);
            return;
        }

        String normalizedKey = createPairKey(businessKey, processInstanceId, participantA, participantB);
        BoundPairState state = new BoundPairState(businessKey, processInstanceId, participantA, participantB);
        boundPairs.put(normalizedKey, state);

        log.info("[DiscordanceMonitor] Marked pair as bound: BK={} | PI={} | {} <-> {} | Key: {}",
            businessKey, processInstanceId, participantA, participantB, normalizedKey);
    }

    /**
     * Unregister a bound pair.
     * This is called when an unbinding task completes or when the process terminates.
     */
    public void markUnbound(String businessKey, String participantA, String participantB) {
        markUnbound(businessKey, null, participantA, participantB);
    }

    public void markUnbound(String businessKey, String processInstanceId, String participantA, String participantB) {
        if (businessKey == null || participantA == null || participantB == null) {
            return;
        }

        String normalizedKey = createPairKey(businessKey, processInstanceId, participantA, participantB);
        BoundPairState removed = boundPairs.remove(normalizedKey);
        if (removed != null) {
            log.info("[DiscordanceMonitor] Removed bound pair: BK={} | PI={} | {} <-> {} | Key: {}",
                    businessKey, processInstanceId, participantA, participantB, normalizedKey);
        }
    }

    /**
     * Listen for position changes and update discordance state
     */
    @EventListener
    public void onParticipantPositionChanged(ParticipantPositionChangedEvent event) {
        String changedParticipantId = event.participantId();

        // Find any bound pairs involving this participant
        for (BoundPairState state : boundPairs.values()) {
            if (!changedParticipantId.equals(state.participantA) && 
                !changedParticipantId.equals(state.participantB)) {
                continue;
            }

            // Get current positions
            String posA = participantDataService.getParticipant(state.participantA)
                    .map(p -> p.getPosition())
                    .orElse(null);
            String posB = participantDataService.getParticipant(state.participantB)
                    .map(p -> p.getPosition())
                    .orElse(null);

            if (posA == null || posB == null) {
                continue;
            }

            if (posA.equals(posB)) {
                // Positions are now concordant - reset error state
                state.discordanceStartTime = -1;
                state.hasRaisedError = false;
                log.info("[DiscordanceMonitor] Pair {} positions are now concordant at position: {}",
                        state.getPairKey(), posA);
            } else {
                // Positions are discordant
                if (state.discordanceStartTime < 0) {
                    // First time we detect discordance - start grace window
                    state.discordanceStartTime = System.currentTimeMillis();
                    log.info("[DiscordanceMonitor] Pair {} detected discordant positions: {} vs {} | Grace window: {}ms",
                            state.getPairKey(), posA, posB, DISCORDANCE_GRACE_WINDOW_MS);
                }
            }
        }
    }

    /**
     * Periodically check if any pair has exceeded the grace window
     */
    @Scheduled(fixedRate = 1000) // Check every 1 second
    public void checkDiscordantBoundParticipants() {
        List<String> pairsToProcess = new ArrayList<>(boundPairs.keySet());
        long now = System.currentTimeMillis();

        for (String pairKey : pairsToProcess) {
            BoundPairState state = boundPairs.get(pairKey);
            if (state == null) {
                continue;
            }

            // Skip if error already raised
            if (state.hasRaisedError) {
                continue;
            }

            // Check if in grace window
            if (state.discordanceStartTime < 0) {
                continue; // Not discordant anymore
            }

            long discordanceElapsed = now - state.discordanceStartTime;
            if (discordanceElapsed >= DISCORDANCE_GRACE_WINDOW_MS) {
                // Grace window expired - raise error
                log.warn("[DiscordanceMonitor] Pair {} grace window expired ({}ms >= {}ms) | Raising discordantPositions exception",
                        pairKey, discordanceElapsed, DISCORDANCE_GRACE_WINDOW_MS);
                
                raiseDiscordanceSignal(state);
                state.hasRaisedError = true;
            }
        }
    }

    /**
     * Emit a discordantPositions signal to the process instance when the grace
     * window expires.
     */
    private void raiseDiscordanceSignal(BoundPairState state) {
        try {
            if (state.processInstanceId == null || state.processInstanceId.isBlank()) {
                log.warn("[DiscordanceMonitor] Missing processInstanceId for pair {}. Signal not sent.", state.getPairKey());
                return;
            }

            Map<String, Object> signalVariables = new HashMap<>();
            signalVariables.put("discordantPositions_participantA", state.participantA);
            signalVariables.put("discordantPositions_participantB", state.participantB);

            List<EventSubscription> subscriptions = runtimeService
                    .createEventSubscriptionQuery()
                    .eventType("signal")
                    .eventName(DISCORDANT_POSITIONS_SIGNAL)
                    .processInstanceId(state.processInstanceId)
                    .list();

                if (subscriptions.isEmpty()) {
                log.warn("[DiscordanceMonitor] No signal subscriptions found for '{}' and PI={} (BK={})",
                    DISCORDANT_POSITIONS_SIGNAL, state.processInstanceId, state.businessKey);
                return;
            }

            for (EventSubscription subscription : subscriptions) {
                runtimeService.signalEventReceived(
                    DISCORDANT_POSITIONS_SIGNAL,
                    subscription.getExecutionId(),
                    signalVariables
                );
            }

            log.info("[DiscordanceMonitor] Sent signal '{}' to PI={} (BK={}) | subscriptions={} | Participants: {} <-> {}",
                    DISCORDANT_POSITIONS_SIGNAL,
                    state.processInstanceId,
                    state.businessKey,
                    subscriptions.size(),
                    state.participantA,
                    state.participantB);
        } catch (Exception e) {
            log.error("[DiscordanceMonitor] Error sending discordance signal for pair {}: {}",
                    state.getPairKey(), e.getMessage(), e);
        }
    }

    /**
     * Create a normalized pair key for consistent lookups
     */
    private String createPairKey(String businessKey, String processInstanceId, String participantA, String participantB) {
        // Sort participants to ensure consistent ordering regardless of the order passed
        String sortedA, sortedB;
        if (participantA.compareTo(participantB) < 0) {
            sortedA = participantA;
            sortedB = participantB;
        } else {
            sortedA = participantB;
            sortedB = participantA;
        }
        String scopeId = processInstanceId != null ? processInstanceId : businessKey;
        return scopeId + ":" + sortedA + "<->" + sortedB;
    }

    /**
     * Get debug info about currently monitored pairs (useful for testing/debugging)
     */
    public List<Map<String, Object>> getMonitoredPairs() {
        List<Map<String, Object>> result = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (BoundPairState state : boundPairs.values()) {
            String posA = participantDataService.getParticipant(state.participantA)
                    .map(p -> p.getPosition())
                    .orElse("unknown");
            String posB = participantDataService.getParticipant(state.participantB)
                    .map(p -> p.getPosition())
                    .orElse("unknown");

            long remainingGraceMs = -1;
            if (state.discordanceStartTime > 0) {
                remainingGraceMs = Math.max(0, 
                        DISCORDANCE_GRACE_WINDOW_MS - (now - state.discordanceStartTime));
            }

            result.add(Map.of(
                    "pairKey", state.getPairKey(),
                    "businessKey", state.businessKey,
                    "processInstanceId", state.processInstanceId != null ? state.processInstanceId : "",
                    "participantA", state.participantA,
                    "participantB", state.participantB,
                    "positionA", posA,
                    "positionB", posB,
                    "concordant", posA.equals(posB),
                    "errorRaised", state.hasRaisedError,
                    "remainingGraceMs", remainingGraceMs
            ));
        }

        return result;
    }
}
