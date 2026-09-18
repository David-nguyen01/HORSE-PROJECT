package com.racehorse.racing.entity;

import com.racehorse.auth.entity.UserAccount;
import com.racehorse.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "race_events")
public class RaceEvent extends BaseEntity {

    @Column(name = "race_name", nullable = false, length = 200)
    private String raceName;

    @Column(nullable = false, length = 200)
    private String racecourse;

    @Column(name = "race_date", nullable = false)
    private LocalDate raceDate;

    @Column(name = "registration_deadline", nullable = false)
    private OffsetDateTime registrationDeadline;

    @Column(name = "distance_meters", nullable = false, precision = 12, scale = 2)
    private BigDecimal distanceMeters;

    @Column(name = "race_class", nullable = false, length = 100)
    private String raceClass;

    @Column(name = "minimum_age_years")
    private Integer minimumAgeYears;

    @Column(name = "maximum_age_years")
    private Integer maximumAgeYears;

    @Column(name = "minimum_weight_kg", precision = 7, scale = 2)
    private BigDecimal minimumWeightKg;

    @Column(name = "maximum_weight_kg", precision = 7, scale = 2)
    private BigDecimal maximumWeightKg;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false, updatable = false)
    private UserAccount createdBy;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    protected RaceEvent() {}

    public RaceEvent(String raceName, String racecourse, LocalDate raceDate, OffsetDateTime registrationDeadline,
                     BigDecimal distanceMeters, String raceClass, Integer minimumAgeYears, Integer maximumAgeYears,
                     BigDecimal minimumWeightKg, BigDecimal maximumWeightKg, UserAccount createdBy) {
        this.raceName = raceName;
        this.racecourse = racecourse;
        this.raceDate = raceDate;
        this.registrationDeadline = registrationDeadline;
        this.distanceMeters = distanceMeters;
        this.raceClass = raceClass;
        this.minimumAgeYears = minimumAgeYears;
        this.maximumAgeYears = maximumAgeYears;
        this.minimumWeightKg = minimumWeightKg;
        this.maximumWeightKg = maximumWeightKg;
        this.createdBy = createdBy;
        this.active = true;
    }

    public void update(String raceName, String racecourse, LocalDate raceDate, OffsetDateTime registrationDeadline,
                       BigDecimal distanceMeters, String raceClass, Integer minimumAgeYears, Integer maximumAgeYears,
                       BigDecimal minimumWeightKg, BigDecimal maximumWeightKg) {
        this.raceName = raceName;
        this.racecourse = racecourse;
        this.raceDate = raceDate;
        this.registrationDeadline = registrationDeadline;
        this.distanceMeters = distanceMeters;
        this.raceClass = raceClass;
        this.minimumAgeYears = minimumAgeYears;
        this.maximumAgeYears = maximumAgeYears;
        this.minimumWeightKg = minimumWeightKg;
        this.maximumWeightKg = maximumWeightKg;
    }

    public void deactivate() {
        this.active = false;
    }

    public String getRaceName() { return raceName; }
    public String getRacecourse() { return racecourse; }
    public LocalDate getRaceDate() { return raceDate; }
    public OffsetDateTime getRegistrationDeadline() { return registrationDeadline; }
    public BigDecimal getDistanceMeters() { return distanceMeters; }
    public String getRaceClass() { return raceClass; }
    public Integer getMinimumAgeYears() { return minimumAgeYears; }
    public Integer getMaximumAgeYears() { return maximumAgeYears; }
    public BigDecimal getMinimumWeightKg() { return minimumWeightKg; }
    public BigDecimal getMaximumWeightKg() { return maximumWeightKg; }
    public UserAccount getCreatedBy() { return createdBy; }
    public boolean isActive() { return active; }
}
