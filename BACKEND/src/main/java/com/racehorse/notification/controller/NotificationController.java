package com.racehorse.notification.controller;

import com.racehorse.notification.dto.NotificationResponse;
import com.racehorse.notification.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.http.MediaType;

@RestController
@RequestMapping("/api/notifications")
@PreAuthorize("isAuthenticated()")
public class NotificationController {

    private final NotificationService service;
    private final com.racehorse.notification.service.SseNotificationService sseService;

    public NotificationController(NotificationService service, com.racehorse.notification.service.SseNotificationService sseService) {
        this.service = service;
        this.sseService = sseService;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal Jwt jwt) {
        return sseService.subscribe(userId(jwt));
    }

    @GetMapping
    Page<NotificationResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.list(userId(jwt), unreadOnly, pageable);
    }

    @PatchMapping("/{notificationId}/read")
    NotificationResponse markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID notificationId) {
        return service.markRead(userId(jwt), notificationId);
    }

    private UUID userId(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
}
