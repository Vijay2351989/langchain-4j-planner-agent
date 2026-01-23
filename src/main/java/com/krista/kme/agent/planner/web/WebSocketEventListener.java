package com.krista.kme.agent.planner.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

/**
 * Listener for WebSocket events to help diagnose connection issues
 */
@Component
public class WebSocketEventListener {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketEventListener.class);

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        
        logger.info("✅ WebSocket CONNECTED - Session: {}", sessionId);
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        
        // Log disconnect reason
        String closeStatus = event.getCloseStatus() != null ? event.getCloseStatus().toString() : "UNKNOWN";
        
        logger.warn("❌ WebSocket DISCONNECTED - Session: {}, Close Status: {}", sessionId, closeStatus);
        
        // Check for common disconnect reasons
        if (closeStatus.contains("1009")) {
            logger.error("🔴 DISCONNECT REASON: Message too large (1009). Increase WebSocket message size limits!");
        } else if (closeStatus.contains("1006")) {
            logger.error("🔴 DISCONNECT REASON: Abnormal closure (1006). Possible network issue or timeout.");
        } else if (closeStatus.contains("1001")) {
            logger.info("Normal disconnect - client going away");
        }
    }

    @EventListener
    public void handleWebSocketSubscribeListener(SessionSubscribeEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        String destination = headerAccessor.getDestination();
        
        logger.info("📡 WebSocket SUBSCRIBED - Session: {}, Destination: {}", sessionId, destination);
    }
}

