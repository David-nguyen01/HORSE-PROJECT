package com.racehorse.health.entity;

import com.racehorse.auth.entity.UserAccount;
import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "injury_recovery_updates")
public class InjuryRecoveryUpdate {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "injury_marker_id", nullable = false)
    private InjuryMarker injuryMarker;

    @Column(name = "status_label", nullable = false, length = 100)
    private String statusLabel;

    @Column(name = "severity_label", length = 100)
    private String severityLabel;

    @Column(nullable = false, length = 4000)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recorded_by_user_id", nullable = false)
    private UserAccount recordedBy;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected InjuryRecoveryUpdate() {}

    public InjuryRecoveryUpdate(InjuryMarker marker, String statusLabel,
                                String severityLabel, String notes,
                                UserAccount recordedBy, Instant recordedAt) {
        this.injuryMarker = marker;
        this.statusLabel = statusLabel;
        this.severityLabel = severityLabel;
        this.notes = notes;
        this.recordedBy = recordedBy;
        this.recordedAt = recordedAt;
    }

    public UUID getId() { return id; }
    public InjuryMarker getInjuryMarker() { return injuryMarker; }
    public String getStatusLabel() { return statusLabel; }
    public String getSeverityLabel() { return severityLabel; }
    public String getNotes() { return notes; }
    public UserAccount getRecordedBy() { return recordedBy; }
    public Instant getRecordedAt() { return recordedAt; }
}
