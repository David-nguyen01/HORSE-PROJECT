package com.racehorse.notification.controller;

import com.racehorse.notification.dto.BroadcastDismissalResponse;
import com.racehorse.notification.dto.BroadcastNotificationResponse;
import com.racehorse.notification.entity.NotificationPriority;
import com.racehorse.notification.repository.NotificationRepository;
import com.racehorse.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class NotificationControllerTest {
    @Test
    void marksAllUnreadForAuthenticatedRecipientBeyondFirstPage() {
        var repository = mock(NotificationRepository.class);
        var now = Instant.parse("2026-10-07T00:00:00Z");
        var recipientId = UUID.randomUUID();
        var service = new NotificationService(repository, Clock.fixed(now, ZoneOffset.UTC));
        var controller = new NotificationController(service);
        var jwt = Jwt.withTokenValue("test").header("alg", "none").subject(recipientId.toString()).build();
        when(repository.markAllRead(recipientId, now)).thenReturn(35);

        assertThat(controller.markAllRead(jwt)).isEqualTo(35);
        verify(repository).markAllRead(recipientId, now);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void listActiveBroadcasts_delegatesToService() {
        var service = mock(NotificationService.class);
        var controller = new NotificationController(service);
        var recipientId = UUID.randomUUID();
        var jwt = Jwt.withTokenValue("test").header("alg", "none").subject(recipientId.toString()).build();
        var expected = List.of(new BroadcastNotificationResponse(
                UUID.randomUUID(), "SERVICE_PACKAGE_DISCONTINUED", NotificationPriority.HIGH,
                "Title", "Message", "SERVICE_PACKAGE", UUID.randomUUID(), Instant.now(), null, false));
        when(service.listActiveBroadcasts(recipientId)).thenReturn(expected);

        var result = controller.listActiveBroadcasts(jwt);

        assertThat(result).isEqualTo(expected);
        verify(service).listActiveBroadcasts(recipientId);
    }

    @Test
    void dismissBroadcast_delegatesToService() {
        var service = mock(NotificationService.class);
        var controller = new NotificationController(service);
        var recipientId = UUID.randomUUID();
        var notifId = UUID.randomUUID();
        var jwt = Jwt.withTokenValue("test").header("alg", "none").subject(recipientId.toString()).build();
        var expected = new BroadcastDismissalResponse(notifId, true, Instant.now());
        when(service.dismissBroadcast(recipientId, notifId)).thenReturn(expected);

        var result = controller.dismissBroadcast(jwt, notifId);

        assertThat(result).isEqualTo(expected);
        verify(service).dismissBroadcast(recipientId, notifId);
    }
}
