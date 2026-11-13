package com.racehorse.notification.service;

import com.racehorse.auth.entity.UserAccount;
import com.racehorse.common.exception.ForbiddenException;
import com.racehorse.common.exception.InvalidInputException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.notification.dto.BroadcastDismissalResponse;
import com.racehorse.notification.dto.BroadcastNotificationResponse;
import com.racehorse.notification.dto.NotificationResponse;
import com.racehorse.notification.entity.Notification;
import com.racehorse.notification.entity.NotificationPriority;
import com.racehorse.notification.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository repository;
    private final Clock clock;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    @Autowired
    public NotificationService(NotificationRepository repository, Clock clock, JdbcTemplate jdbc, ObjectMapper json) {
        this.repository = repository;
        this.clock = clock;
        this.jdbc = jdbc;
        this.json = json;
    }

    public NotificationService(NotificationRepository repository, Clock clock) {
        this(repository, clock, null, null);
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

    @Transactional(readOnly = true)
    public List<BroadcastNotificationResponse> listActiveBroadcasts(UUID recipientId) {
        if (jdbc == null) return List.of();
        String sql = """
                select n.id, n.notification_type, n.priority, n.title, n.message,
                       n.related_entity_type, n.related_entity_id, n.created_at, n.read_at
                from dbo.notifications n
                where n.recipient_user_id = ?
                  and n.notification_type = 'SERVICE_PACKAGE_DISCONTINUED'
                  and not exists (
                      select 1 from dbo.module_records mr
                      where mr.module_code = 'SYSTEM'
                        and mr.record_type = 'PACKAGE_DISCONTINUATION_DISMISSAL'
                        and mr.user_id = n.recipient_user_id
                        and lower(JSON_VALUE(mr.data_json, '$.notificationId')) = lower(cast(n.id as varchar(36)))
                  )
                order by n.created_at desc
                """;
        return jdbc.query(sql, (rs, rowNum) -> {
            var createdTs = rs.getTimestamp("created_at");
            var readTs = rs.getTimestamp("read_at");
            return new BroadcastNotificationResponse(
                    rs.getObject("id", UUID.class),
                    rs.getString("notification_type"),
                    NotificationPriority.valueOf(rs.getString("priority")),
                    rs.getString("title"),
                    rs.getString("message"),
                    rs.getString("related_entity_type"),
                    rs.getObject("related_entity_id", UUID.class),
                    createdTs != null ? createdTs.toInstant() : null,
                    readTs != null ? readTs.toInstant() : null,
                    false
            );
        }, recipientId);
    }

    @Transactional
    public BroadcastDismissalResponse dismissBroadcast(UUID recipientId, UUID notificationId) {
        Notification notification = repository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông báo: " + notificationId));

        if (!notification.getRecipient().getId().equals(recipientId)) {
            throw new ForbiddenException("Bạn không có quyền thao tác trên thông báo này");
        }

        if (!"SERVICE_PACKAGE_DISCONTINUED".equals(notification.getNotificationType())) {
            throw new InvalidInputException("Chỉ có thể xác nhận thông báo phát thanh gói dịch vụ");
        }

        Instant now = clock.instant();

        if (jdbc != null) {
            Integer count = jdbc.queryForObject(
                    "select count(*) from dbo.module_records where module_code='SYSTEM' and record_type='PACKAGE_DISCONTINUATION_DISMISSAL' "
                            + "and user_id=? and lower(JSON_VALUE(data_json, '$.notificationId'))=lower(?)",
                    Integer.class, recipientId, notificationId.toString());

            if (count == null || count == 0) {
                UUID recordId = UUID.randomUUID();
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("notificationId", notificationId.toString());
                data.put("packageId", notification.getRelatedEntityId() != null ? notification.getRelatedEntityId().toString() : null);
                data.put("dismissedAt", now.toString());
                data.put("confirmed", true);

                try {
                    jdbc.update("insert into dbo.module_records(id,module_code,record_type,user_id,status,event_date,data_json) "
                                    + "values(?,'SYSTEM','PACKAGE_DISCONTINUATION_DISMISSAL',?,'DISMISSED',?,?)",
                            recordId, recipientId, LocalDate.ofInstant(now, ZoneOffset.UTC),
                            json != null ? json.writeValueAsString(data) : "{}");
                } catch (Exception e) {
                    throw new RuntimeException("Lỗi ghi nhận đóng thông báo", e);
                }
            }
        }

        if (notification.getReadAt() == null) {
            notification.markRead(now);
            repository.save(notification);
        }

        return new BroadcastDismissalResponse(notificationId, true, now);
    }

    private NotificationResponse toResponse(Notification value) {
        return new NotificationResponse(
                value.getId(), value.getNotificationType(), value.getPriority(), value.getTitle(),
                value.getMessage(), value.getRelatedEntityType(), value.getRelatedEntityId(),
                value.getCreatedAt(), value.getReadAt());
    }
}
