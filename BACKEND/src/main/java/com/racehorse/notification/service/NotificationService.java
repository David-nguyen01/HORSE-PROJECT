package com.racehorse.notification.service;

import com.racehorse.auth.entity.UserAccount;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.notification.dto.NotificationResponse;
import com.racehorse.notification.entity.Notification;
import com.racehorse.notification.entity.NotificationPriority;
import com.racehorse.notification.repository.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository repository;
    private final Clock clock;

    public NotificationService(NotificationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public Notification create(
            UserAccount recipient,
            String type,
            NotificationPriority priority,
            String title,
            String message,
            String relatedEntityType,
            UUID relatedEntityId) {
        return repository.save(new Notification(
                recipient, type, priority, title, message, relatedEntityType, relatedEntityId, clock.instant()));
    }

    @Transactional
    public Notification createDeduplicated(UserAccount recipient, String type, NotificationPriority priority,
            String title, String message, String relatedEntityType, UUID relatedEntityId, String key) {
        if (key == null || key.isBlank() || key.length() > 200)
            throw new IllegalArgumentException("A valid notification deduplication key is required");
        return repository.findByDeduplicationKey(key).orElseGet(() -> repository.save(new Notification(
                recipient, type, priority, title, message, relatedEntityType, relatedEntityId, key, clock.instant())));
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> list(UUID recipientId, boolean unreadOnly, Pageable pageable) {
        Page<Notification> page = unreadOnly
                ? repository.findByRecipientIdAndReadAtIsNullOrderByCreatedAtDesc(recipientId, pageable)
                : repository.findByRecipientIdOrderByCreatedAtDesc(recipientId, pageable);
        return page.map(this::toResponse);
    }

    @Transactional
    public NotificationResponse markRead(UUID recipientId, UUID notificationId) {
        Notification notification = repository.findByIdAndRecipientId(notificationId, recipientId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông báo: " + notificationId));
        notification.markRead(clock.instant());
        return toResponse(repository.save(notification));
    }

    @Transactional
    public int markAllRead(UUID recipientId) {
        return repository.markAllRead(recipientId, clock.instant());
    }

    private NotificationResponse toResponse(Notification value) {
        return new NotificationResponse(
                value.getId(), value.getNotificationType(), value.getPriority(), value.getTitle(),
                value.getMessage(), value.getRelatedEntityType(), value.getRelatedEntityId(),
                value.getCreatedAt(), value.getReadAt());
    }
}
