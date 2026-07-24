package org.unicam.intermediate.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.unicam.intermediate.models.dto.Response;
import org.unicam.intermediate.service.websocket.WebSocketSessionManager;

import java.util.Map;

@RestController
@RequestMapping("/api/ws/status")
@RequiredArgsConstructor
@Slf4j
public class WebSocketStatusController {

    private final WebSocketSessionManager sessionManager;

    @GetMapping("/connected/{userId}")
    public ResponseEntity<Response<Boolean>> isUserConnected(@PathVariable String userId) {
        boolean connected = sessionManager.isUserConnected(userId);
        return ResponseEntity.ok(Response.ok(connected));
    }

    @GetMapping("/stats")
    public ResponseEntity<Response<Map<String, Object>>> getStats() {
        Map<String, Object> stats = Map.of(
            "activeSessions", sessionManager.getActiveSessionCount(),
            "connectedUsers", sessionManager.getConnectedUsers().size(),
            "users", sessionManager.getConnectedUsers()
        );
        return ResponseEntity.ok(Response.ok(stats));
    }

    @GetMapping("/tracking/{userId}")
    public ResponseEntity<Response<String>> getTrackingProcess(@PathVariable String userId) {
        String processId = sessionManager.getTrackingProcess(userId);
        return ResponseEntity.ok(Response.ok(processId));
    }
}