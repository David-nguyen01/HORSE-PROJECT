package com.racehorse.health.entity;

import com.racehorse.auth.entity.UserAccount;
import com.racehorse.horse.entity.Horse;
import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "injury_markers")
public class InjuryMarker {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "horse_id", nullable = false)
    private Horse horse;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @Column(name = "body_part_label", nullable = false, length = 150)
    private String bodyPartLabel;

    @Column(name = "coordinate_x", nullable = false, precision = 12, scale = 6)
    private BigDecimal coordinateX;

    @Column(name = "coordinate_y", nullable = false, precision = 12, scale = 6)
    private BigDecimal coordinateY;

    @Column(name = "coordinate_z", nullable = false, precision = 12, scale = 6)
    private BigDecimal coordinateZ;

    @Column(name = "initial_severity", nullable = false, length = 100)
    private String initialSeverity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private UserAccount createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "injuryMarker", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("recordedAt ASC")
    private List<InjuryRecoveryUpdate> recoveryUpdates = new ArrayList<>();

    protected InjuryMarker() {}

    public InjuryMarker(Horse horse, String modelName, String bodyPartLabel,
                        BigDecimal coordinateX, BigDecimal coordinateY, BigDecimal coordinateZ,
                        String initialSeverity, UserAccount createdBy, Instant createdAt) {
        this.horse = horse;
        this.modelName = modelName;
        this.bodyPartLabel = bodyPartLabel;
        this.coordinateX = coordinateX;
        this.coordinateY = coordinateY;
        this.coordinateZ = coordinateZ;
        this.initialSeverity = initialSeverity;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public Horse getHorse() { return horse; }
    public String getModelName() { return modelName; }
    public String getBodyPartLabel() { return bodyPartLabel; }
    public BigDecimal getCoordinateX() { return coordinateX; }
    public BigDecimal getCoordinateY() { return coordinateY; }
    public BigDecimal getCoordinateZ() { return coordinateZ; }
    public String getInitialSeverity() { return initialSeverity; }
    public UserAccount getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public List<InjuryRecoveryUpdate> getRecoveryUpdates() { return recoveryUpdates; }

    /** Returns the latest recovery severity or initial severity if no updates yet. */
    public String currentSeverity() {
        if (recoveryUpdates.isEmpty()) return initialSeverity;
        return recoveryUpdates.getLast().getSeverityLabel() != null
               ? recoveryUpdates.getLast().getSeverityLabel()
               : initialSeverity;
    }
}
