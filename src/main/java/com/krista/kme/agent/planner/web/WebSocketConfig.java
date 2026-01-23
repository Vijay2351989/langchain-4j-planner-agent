package com.krista.kme.agent.planner.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * WebSocket configuration for real-time communication with support for large messages
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketConfig.class);

    // Increase limits to handle large JSON payloads (10MB)
    private static final int MESSAGE_SIZE_LIMIT = 10 * 1024 * 1024; // 10MB
    private static final int TEXT_MESSAGE_SIZE_LIMIT = 10 * 1024 * 1024; // 10MB for text messages
    private static final int BINARY_MESSAGE_SIZE_LIMIT = 10 * 1024 * 1024; // 10MB for binary messages
    private static final int SEND_TIME_LIMIT = 60 * 1000; // 60 seconds
    private static final int SEND_BUFFER_SIZE_LIMIT = 10 * 1024 * 1024; // 10MB

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
        logger.info("WebSocket message broker configured");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS()
                .setStreamBytesLimit(MESSAGE_SIZE_LIMIT)
                .setHttpMessageCacheSize(1000)
                .setDisconnectDelay(30 * 1000); // 30 seconds before disconnect

        logger.info("WebSocket STOMP endpoint registered at /ws with message size limit: {} bytes",
                   MESSAGE_SIZE_LIMIT);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration
                .setMessageSizeLimit(MESSAGE_SIZE_LIMIT)
                .setSendBufferSizeLimit(SEND_BUFFER_SIZE_LIMIT)
                .setSendTimeLimit(SEND_TIME_LIMIT);

        logger.info("WebSocket transport configured - Message size: {} bytes, Send buffer: {} bytes, Send timeout: {} ms",
                   MESSAGE_SIZE_LIMIT, SEND_BUFFER_SIZE_LIMIT, SEND_TIME_LIMIT);
    }

    /**
     * Configure the WebSocket container to support large text and binary messages.
     * This is critical to prevent "message too big for output buffer" errors (code 1009).
     */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(TEXT_MESSAGE_SIZE_LIMIT);
        container.setMaxBinaryMessageBufferSize(BINARY_MESSAGE_SIZE_LIMIT);
        container.setMaxSessionIdleTimeout(300000L); // 5 minutes

        logger.info("WebSocket container configured - Text message buffer: {} bytes ({} MB), Binary message buffer: {} bytes ({} MB)",
                   TEXT_MESSAGE_SIZE_LIMIT, TEXT_MESSAGE_SIZE_LIMIT / (1024 * 1024),
                   BINARY_MESSAGE_SIZE_LIMIT, BINARY_MESSAGE_SIZE_LIMIT / (1024 * 1024));

        return container;
    }
}

