package com.racehorse.notification.controller;

import com.racehorse.notification.repository.NotificationRepository;
import com.racehorse.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
}
