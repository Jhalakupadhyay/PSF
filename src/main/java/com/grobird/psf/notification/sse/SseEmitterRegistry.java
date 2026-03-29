package com.grobird.psf.notification.sse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grobird.psf.notification.dto.NotificationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Registry for managing SSE connections per sales user.
 * When a notification is created, emits the event to all connected clients for that user.
 */
@Component
public class SseEmitterRegistry {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterRegistry.class);

    private final ConcurrentHashMap<Long, Set<SseEmitter>> emittersByUserId = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public SseEmitterRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Register an SSE emitter for the given sales user.
     * Attaches callbacks for cleanup on completion, timeout, or error.
     */
    public void register(Long salesUserId, SseEmitter emitter) {
        emittersByUserId.computeIfAbsent(salesUserId, k -> new CopyOnWriteArraySet<>()).add(emitter);

        Runnable cleanup = () -> remove(salesUserId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> {
            log.debug("SSE error for user {}: {}", salesUserId, e.getMessage());
            cleanup.run();
        });

        log.debug("SSE emitter registered for user {}", salesUserId);
    }

    /**
     * Remove an emitter for the given user (called on disconnect/error/timeout).
     */
    public void remove(Long salesUserId, SseEmitter emitter) {
        Set<SseEmitter> emitters = emittersByUserId.get(salesUserId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                emittersByUserId.remove(salesUserId);
            }
        }
        log.debug("SSE emitter removed for user {}", salesUserId);
    }

    /**
     * Emit a notification event to all connected SSE clients for the given sales user.
     */
    public void emit(Long salesUserId, NotificationResponse notification) {
        Set<SseEmitter> emitters = emittersByUserId.get(salesUserId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        String json;
        try {
            json = objectMapper.writeValueAsString(notification);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize notification for SSE: {}", e.getMessage());
            return;
        }

        SseEmitter.SseEventBuilder event = SseEmitter.event()
                .name("notification")
                .data(json);

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(event);
            } catch (IOException e) {
                log.debug("Failed to send SSE event to user {}, removing emitter: {}", salesUserId, e.getMessage());
                remove(salesUserId, emitter);
            }
        }
    }

    /**
     * Check if a user has any active SSE connections.
     */
    public boolean hasActiveConnections(Long salesUserId) {
        Set<SseEmitter> emitters = emittersByUserId.get(salesUserId);
        return emitters != null && !emitters.isEmpty();
    }
}
