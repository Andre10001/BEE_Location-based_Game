package org.unicam.intermediate.service.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.unicam.intermediate.models.dto.websocket.GpsResponse;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketSessionManager {
    
    private final ObjectMapper objectMapper;
    
    // userId -> Set of sessions (user might have multiple devices)
    private final Map<String, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();
    
    // userId -> processId being tracked
    private final Map<String, String> userTrackingProcess = new ConcurrentHashMap<>();
    
    // sessionId -> userId for reverse lookup
    private final Map<String, String> sessionUserMap = new ConcurrentHashMap<>();

    public synchronized void addSession(String userId, WebSocketSession session) {
        userSessions.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(session);
        sessionUserMap.put(session.getId(), userId);
        log.debug("[SessionManager] Added session {} for user {}", session.getId(), userId);
    }

    public synchronized void removeSession(String userId, String sessionId) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions != null) {
            sessions.removeIf(s -> s.getId().equals(sessionId));
            if (sessions.isEmpty()) {
                userSessions.remove(userId);
                userTrackingProcess.remove(userId);
            }
        }
        sessionUserMap.remove(sessionId);
        log.debug("[SessionManager] Removed session {} for user {}", sessionId, userId);
    }

    public void broadcastToUser(String userId, GpsResponse response, String excludeSessionId) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions != null) {
            String message;
            try {
                message = objectMapper.writeValueAsString(response);
            } catch (Exception e) {
                log.error("[SessionManager] Failed to serialize response", e);
                return;
            }
            
            for (WebSocketSession session : sessions) {
                if (!session.getId().equals(excludeSessionId) && session.isOpen()) {
                    try {
                        session.sendMessage(new TextMessage(message));
                    } catch (IOException e) {
                        log.error("[SessionManager] Failed to send to session {}", session.getId(), e);
                    }
                }
            }
        }
    }

    public void setTrackingProcess(String userId, String processId) {
        userTrackingProcess.put(userId, processId);
    }

    public void clearTrackingProcess(String userId) {
        userTrackingProcess.remove(userId);
    }

    public String getTrackingProcess(String userId) {
        return userTrackingProcess.get(userId);
    }

    public boolean isUserConnected(String userId) {
        return userSessions.containsKey(userId) && !userSessions.get(userId).isEmpty();
    }

    public int getActiveSessionCount() {
        return userSessions.values().stream().mapToInt(Set::size).sum();
    }

    public Set<String> getConnectedUsers() {
        return userSessions.keySet();
    }
}