package com.grobird.psf.notification.controller;

import com.grobird.psf.config.security.UserPrincipal;
import com.grobird.psf.notification.dto.NotificationResponse;
import com.grobird.psf.notification.dto.NotificationStatusFilter;
import com.grobird.psf.notification.service.NotificationService;
import com.grobird.psf.notification.sse.SseEmitterRegistry;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@PreAuthorize("hasAnyRole('SALES', 'ADMIN')")
public class NotificationController {

    private static final long SSE_TIMEOUT_MS = 30 * 60 * 1000L; // 30 minutes

    private final NotificationService notificationService;
    private final SseEmitterRegistry sseEmitterRegistry;

    public NotificationController(NotificationService notificationService,
                                  SseEmitterRegistry sseEmitterRegistry) {
        this.notificationService = notificationService;
        this.sseEmitterRegistry = sseEmitterRegistry;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal UserPrincipal principal) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        sseEmitterRegistry.register(principal.getUserId(), emitter);
        return emitter;
    }

    @GetMapping
    public ResponseEntity<List<NotificationResponse>> list(
            @RequestParam(required = false, defaultValue = "all") String status,
            @AuthenticationPrincipal UserPrincipal principal) {
        NotificationStatusFilter filter = NotificationStatusFilter.from(status);
        List<NotificationResponse> notifications = notificationService.listForCurrentUser(principal, filter);
        return ResponseEntity.ok(notifications);
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markRead(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        NotificationResponse updated = notificationService.markAsRead(id, principal);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(@AuthenticationPrincipal UserPrincipal principal) {
        long unreadCount = notificationService.getUnreadCount(principal);
        return ResponseEntity.ok(Map.of("unreadCount", unreadCount));
    }
}
