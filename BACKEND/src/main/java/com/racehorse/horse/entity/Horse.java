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

import java.time.LocalDate;
import java.time.Instant;

@Entity
@Table(name = "horses")
public class Horse extends BaseEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "microchip_passport_number", unique = true, length = 100)
    private String microchipPassportNumber;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HorseGender gender;

    @Column(name = "color_markings", nullable = false, length = 500)
    private String colorMarkings;

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

    @Column(name = "stable_location", nullable = false, length = 150)
    private String stableLocation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HorseStatus status;

    @Column(name = "archived_at")
    private Instant archivedAt;

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

    public Instant getArchivedAt() {
        return archivedAt;
    }
}
