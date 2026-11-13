package com.racehorse.notification.controller;

import com.racehorse.notification.dto.BroadcastDismissalResponse;
import com.racehorse.notification.dto.BroadcastNotificationResponse;
import com.racehorse.notification.dto.NotificationResponse;
import com.racehorse.notification.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@PreAuthorize("isAuthenticated()")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
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

    @PatchMapping("/read-all")
    int markAllRead(@AuthenticationPrincipal Jwt jwt) {
        return service.markAllRead(userId(jwt));
    }

    @GetMapping("/broadcasts")
    @PreAuthorize("hasRole('HORSE_OWNER')")
    public List<BroadcastNotificationResponse> listActiveBroadcasts(@AuthenticationPrincipal Jwt jwt) {
        return service.listActiveBroadcasts(userId(jwt));
    }

    @PostMapping("/broadcasts/{notificationId}/dismiss")
    @PreAuthorize("hasRole('HORSE_OWNER')")
    public BroadcastDismissalResponse dismissBroadcast(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID notificationId) {
        return service.dismissBroadcast(userId(jwt), notificationId);
    }

    @PatchMapping("/broadcasts/{notificationId}/dismiss")
    @PreAuthorize("hasRole('HORSE_OWNER')")
    public BroadcastDismissalResponse dismissBroadcastPatch(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID notificationId) {
        return service.dismissBroadcast(userId(jwt), notificationId);
    }

    private UUID userId(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
}
