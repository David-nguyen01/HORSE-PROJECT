package com.racehorse.notification.entity;

import com.racehorse.auth.entity.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_user_id", nullable = false)
    private UserAccount recipient;

    @Column(name = "notification_type", nullable = false, length = 50)
    private String notificationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private NotificationPriority priority;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 2000)
    private String message;

    @Column(name = "related_entity_type", length = 80)
    private String relatedEntityType;

    @Column(name = "related_entity_id")
    private UUID relatedEntityId;

    @Column(name = "deduplication_key", length = 200)
    private String deduplicationKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    protected Notification() {
    }

    public Notification(
            UserAccount recipient,
            String notificationType,
            NotificationPriority priority,
            String title,
            String message,
            String relatedEntityType,
            UUID relatedEntityId,
            Instant createdAt) {
        this(recipient, notificationType, priority, title, message, relatedEntityType, relatedEntityId, null, createdAt);
    }

    public Notification(UserAccount recipient, String notificationType, NotificationPriority priority,
            String title, String message, String relatedEntityType, UUID relatedEntityId,
            String deduplicationKey, Instant createdAt) {
        this.recipient = recipient;
        this.notificationType = notificationType;
        this.priority = priority;
        this.title = title;
        this.message = message;
        this.relatedEntityType = relatedEntityType;
        this.relatedEntityId = relatedEntityId;
        this.deduplicationKey = deduplicationKey;
        this.createdAt = createdAt;
    }

    public void markRead(Instant time) { if (readAt == null) readAt = time; }

    public UUID getId() { return id; }
    public UserAccount getRecipient() { return recipient; }
    public String getNotificationType() { return notificationType; }
    public NotificationPriority getPriority() { return priority; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public String getRelatedEntityType() { return relatedEntityType; }
    public UUID getRelatedEntityId() { return relatedEntityId; }
    public String getDeduplicationKey() { return deduplicationKey; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReadAt() { return readAt; }
}
