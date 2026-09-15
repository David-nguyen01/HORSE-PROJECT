package com.racehorse.health.entity;

import com.racehorse.auth.entity.UserAccount;
import com.racehorse.horse.entity.Horse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "training_locks")
public class TrainingLock {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "horse_id", nullable = false)
    private Horse horse;

    @Column(nullable = false, length = 2000)
    private String reason;

    @Column(name = "review_date")
    private LocalDate reviewDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "locked_by_user_id", nullable = false)
    private UserAccount lockedBy;

    @Column(name = "locked_at", nullable = false, updatable = false)
    private Instant lockedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "released_by_user_id")
    private UserAccount releasedBy;

    @Column(name = "release_reason", length = 2000)
    private String releaseReason;

    @Column(name = "released_at")
    private Instant releasedAt;

    protected TrainingLock() {
    }

    public TrainingLock(Horse horse, String reason, LocalDate reviewDate, UserAccount lockedBy, Instant lockedAt) {
        this.horse = horse;
        this.reason = reason;
        this.reviewDate = reviewDate;
        this.lockedBy = lockedBy;
        this.lockedAt = lockedAt;
    }

    public void release(UserAccount releasedBy, String releaseReason, Instant releasedAt) {
        if (this.releasedAt != null) throw new IllegalStateException("Khóa huấn luyện đã được gỡ");
        this.releasedBy = releasedBy;
        this.releaseReason = releaseReason;
        this.releasedAt = releasedAt;
    }

    public UUID getId() { return id; }
    public Horse getHorse() { return horse; }
    public String getReason() { return reason; }
    public LocalDate getReviewDate() { return reviewDate; }
    public UserAccount getLockedBy() { return lockedBy; }
    public Instant getLockedAt() { return lockedAt; }
    public UserAccount getReleasedBy() { return releasedBy; }
    public String getReleaseReason() { return releaseReason; }
    public Instant getReleasedAt() { return releasedAt; }
    public boolean isActive() { return releasedAt == null; }
}
