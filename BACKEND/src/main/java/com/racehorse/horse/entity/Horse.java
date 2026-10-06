package com.racehorse.horse.entity;

import com.racehorse.auth.entity.UserAccount;
import com.racehorse.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Nationalized;

import java.time.LocalDate;
import java.time.Instant;

@Entity
@Table(name = "horses")
public class Horse extends BaseEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Nationalized
    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "microchip_passport_number", unique = true, length = 100)
    private String microchipPassportNumber;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HorseGender gender;

    @Nationalized
    @Column(name = "color_markings", nullable = false, length = 500)
    private String colorMarkings;

    @Nationalized
    @Column(nullable = false, length = 100)
    private String breed;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sire_id")
    private Horse sire;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dam_id")
    private Horse dam;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_trainer_id")
    private UserAccount currentTrainer;

    @Nationalized
    @Column(name = "stable_location", nullable = false, length = 150)
    private String stableLocation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HorseStatus status;

    @Column(name = "health_status", length = 20)
    private String healthStatus;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "payment_status", length = 20)
    private String paymentStatus = "UNPAID";

    @Column(name = "service_override")
    private Boolean serviceOverride = false;

    @Column(name = "override_reason", length = 300)
    private String overrideReason;

    @Column(name = "paid_until")
    private LocalDate paidUntil;

    protected Horse() {
    }

    public Horse(
            String code,
            String name,
            String microchipPassportNumber,
            LocalDate birthDate,
            HorseGender gender,
            String colorMarkings,
            String breed,
            String stableLocation,
            HorseStatus status) {
        this.code = code;
        this.name = name;
        this.microchipPassportNumber = microchipPassportNumber;
        this.birthDate = birthDate;
        this.gender = gender;
        this.colorMarkings = colorMarkings;
        this.breed = breed;
        this.stableLocation = stableLocation;
        this.status = status;
    }

    public void assignParents(Horse sire, Horse dam) {
        this.sire = sire;
        this.dam = dam;
    }

    public void assignCurrentTrainer(UserAccount currentTrainer) {
        this.currentTrainer = currentTrainer;
    }

    public void update(
            String name,
            String microchipPassportNumber,
            LocalDate birthDate,
            HorseGender gender,
            String colorMarkings,
            String breed,
            String stableLocation,
            HorseStatus status) {
        this.name = name;
        this.microchipPassportNumber = microchipPassportNumber;
        this.birthDate = birthDate;
        this.gender = gender;
        this.colorMarkings = colorMarkings;
        this.breed = breed;
        this.stableLocation = stableLocation;
        this.status = status;
    }

    public void archive(Instant archivedAt) {
        if (this.archivedAt == null) {
            this.archivedAt = archivedAt;
        }
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getMicrochipPassportNumber() {
        return microchipPassportNumber;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public HorseGender getGender() {
        return gender;
    }

    public String getColorMarkings() {
        return colorMarkings;
    }

    public String getBreed() {
        return breed;
    }

    public Horse getSire() {
        return sire;
    }

    public Horse getDam() {
        return dam;
    }

    public UserAccount getCurrentTrainer() {
        return currentTrainer;
    }

    public String getStableLocation() {
        return stableLocation;
    }

    public HorseStatus getStatus() {
        return status;
    }

    public String getHealthStatus() {
        return healthStatus;
    }

    public void clearOperationalInjury() {
        if (this.status == HorseStatus.INJURED) this.status = HorseStatus.ACTIVE;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public String getPaymentStatus() {
        return paymentStatus == null ? "UNPAID" : paymentStatus;
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public Boolean getServiceOverride() {
        return Boolean.TRUE.equals(serviceOverride);
    }

    public void setServiceOverride(Boolean serviceOverride) {
        this.serviceOverride = serviceOverride;
    }

    public String getOverrideReason() {
        return overrideReason;
    }

    public void setOverrideReason(String overrideReason) {
        this.overrideReason = overrideReason;
    }

    public LocalDate getPaidUntil() {
        return paidUntil;
    }

    public void setPaidUntil(LocalDate paidUntil) {
        this.paidUntil = paidUntil;
    }
}
