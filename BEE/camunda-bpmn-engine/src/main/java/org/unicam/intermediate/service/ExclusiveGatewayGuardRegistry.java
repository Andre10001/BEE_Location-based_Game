package org.unicam.intermediate.service;

import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.RuntimeService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.unicam.intermediate.models.record.GatewayWaitInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class ExclusiveGatewayGuardRegistry {

    private static final String SELECTED_FLOW_VAR = "selectedSequenceFlowId";
    private final Map<String, GatewayWaitInfo> activeGateways = new ConcurrentHashMap<>();

    private final RuntimeService runtimeService;
    private final SequenceFlowGuardEvaluator sequenceFlowGuardEvaluator;

    public ExclusiveGatewayGuardRegistry(RuntimeService runtimeService,
                                         SequenceFlowGuardEvaluator sequenceFlowGuardEvaluator) {
        this.runtimeService = runtimeService;
        this.sequenceFlowGuardEvaluator = sequenceFlowGuardEvaluator;
    }

    public void registerGateway(String executionId,
                                String gatewayId,
                                String processDefinitionId,
                                String participantId,
                                List<String> outgoingFlowIds) {
        if (executionId == null || gatewayId == null || processDefinitionId == null || outgoingFlowIds == null || outgoingFlowIds.isEmpty()) {
            return;
        }

        GatewayWaitInfo info = new GatewayWaitInfo(executionId, gatewayId, processDefinitionId, participantId, List.copyOf(outgoingFlowIds));
        activeGateways.put(executionId, info);

        log.info("[ExclusiveGatewayRegistry] Registered gateway {} (execution={}) with {} guarded outgoing flows",
                gatewayId, executionId, outgoingFlowIds.size());
    }

    public void removeGateway(String executionId) {
        if (executionId == null) {
            return;
        }

        GatewayWaitInfo removed = activeGateways.remove(executionId);
        if (removed != null) {
            log.info("[ExclusiveGatewayRegistry] Removed gateway {} (execution={})",
                    removed.gatewayId(), executionId);
        }
    }

    public void removeGateway(String executionId, String expectedGatewayId) {
        if (executionId == null || expectedGatewayId == null) {
            return;
        }

        GatewayWaitInfo current = activeGateways.get(executionId);
        if (current == null || !expectedGatewayId.equals(current.gatewayId())) {
            return;
        }

        if (activeGateways.remove(executionId, current)) {
            log.info("[ExclusiveGatewayRegistry] Removed gateway {} (execution={})",
                    current.gatewayId(), executionId);
        }
    }

    @Scheduled(fixedRate = 2000)
    public void checkWaitingGateways() {
        if (activeGateways.isEmpty()) {
            return;
        }

        List<GatewayWaitInfo> completedGateways = new ArrayList<>();

        for (GatewayWaitInfo gatewayInfo : activeGateways.values()) {
            String selectedFlowId = selectFirstSatisfiedFlow(gatewayInfo);
            if (selectedFlowId == null) {
                continue;
            }

            try {
                runtimeService.setVariable(gatewayInfo.executionId(), SELECTED_FLOW_VAR, selectedFlowId);
                runtimeService.signal(gatewayInfo.executionId());
                completedGateways.add(gatewayInfo);

                log.info("[ExclusiveGatewayRegistry] Signaled gateway {} (execution={}) with selected flow {}",
                        gatewayInfo.gatewayId(), gatewayInfo.executionId(), selectedFlowId);
            } catch (Exception e) {
                log.error("[ExclusiveGatewayRegistry] Failed to signal gateway {} (execution={}): {}",
                        gatewayInfo.gatewayId(), gatewayInfo.executionId(), e.getMessage(), e);
            }
        }

        completedGateways.forEach(gatewayInfo ->
            removeGateway(gatewayInfo.executionId(), gatewayInfo.gatewayId()));
    }

    private String selectFirstSatisfiedFlow(GatewayWaitInfo gatewayInfo) {
        for (String flowId : gatewayInfo.outgoingFlowIds()) {
            boolean satisfied = sequenceFlowGuardEvaluator.evaluateGuard(
                    gatewayInfo.processDefinitionId(),
                    flowId,
                    gatewayInfo.participantId(),
                    gatewayInfo.executionId());
            if (satisfied) {
                return flowId;
            }
        }

        return null;
    }
}
