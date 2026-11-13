package com.racehorse.servicepackage.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "service_packages", schema = "dbo")
public class ServicePackageEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "price_vnd", nullable = false)
    private BigDecimal priceVnd;

    @Column(name = "duration_days", nullable = false)
    private int durationDays;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Column(name = "updated_by_user_id")
    private UUID updatedByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public ServicePackageEntity() {}

    public ServicePackageEntity(UUID id, String code, String name, String description,
                                BigDecimal priceVnd, int durationDays, boolean isActive,
                                int displayOrder, UUID createdByUserId, UUID updatedByUserId,
                                OffsetDateTime createdAt, OffsetDateTime updatedAt, long version) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.description = description;
        this.priceVnd = priceVnd;
        this.durationDays = durationDays;
        this.isActive = isActive;
        this.displayOrder = displayOrder;
        this.createdByUserId = createdByUserId;
        this.updatedByUserId = updatedByUserId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    @PrePersist
    void onPrePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (updatedAt == null) updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    void onPreUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getPriceVnd() { return priceVnd; }
    public void setPriceVnd(BigDecimal priceVnd) { this.priceVnd = priceVnd; }

    public int getDurationDays() { return durationDays; }
    public void setDurationDays(int durationDays) { this.durationDays = durationDays; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }

    public UUID getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(UUID createdByUserId) { this.createdByUserId = createdByUserId; }

    public UUID getUpdatedByUserId() { return updatedByUserId; }
    public void setUpdatedByUserId(UUID updatedByUserId) { this.updatedByUserId = updatedByUserId; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
}
