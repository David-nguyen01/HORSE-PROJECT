package com.racehorse.audit.entity;

import com.racehorse.auth.entity.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.springframework.data.annotation.Immutable;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
@Immutable
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private UserAccount actor;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "module_code", nullable = false, updatable = false, length = 50)
    private String moduleCode;

    @Column(name = "action_code", nullable = false, updatable = false, length = 30)
    private String actionCode;

    @Column(name = "entity_type", updatable = false, length = 80)
    private String entityType;

    @Column(name = "entity_id", updatable = false)
    private UUID entityId;

    @Column(nullable = false, updatable = false, length = 2000)
    private String description;

    @Column(name = "old_values_json", updatable = false)
    private String oldValuesJson;

    @Column(name = "new_values_json", updatable = false)
    private String newValuesJson;

    @Column(name = "ip_address", updatable = false, length = 45)
    private String ipAddress;

    @Column(name = "user_agent", updatable = false, length = 500)
    private String userAgent;

    protected AuditLog() {}

    public Long getId() { return id; }
    public UserAccount getActor() { return actor; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public String getModuleCode() { return moduleCode; }
    public String getActionCode() { return actionCode; }
    public String getEntityType() { return entityType; }
    public UUID getEntityId() { return entityId; }
    public String getDescription() { return description; }
    public String getOldValuesJson() { return oldValuesJson; }
    public String getNewValuesJson() { return newValuesJson; }
    public String getIpAddress() { return ipAddress; }
    public String getUserAgent() { return userAgent; }
}
