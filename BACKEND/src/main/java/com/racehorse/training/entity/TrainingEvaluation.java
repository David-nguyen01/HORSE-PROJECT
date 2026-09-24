package com.racehorse.training.entity;

import com.racehorse.auth.entity.UserAccount;
import com.racehorse.common.persistence.BaseEntity;
import com.racehorse.horse.entity.Horse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "training_evaluations")
public class TrainingEvaluation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "horse_id", nullable = false)
    private Horse horse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "training_schedule_id")
    private TrainingSchedule trainingSchedule;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recorded_by_user_id", nullable = false)
    private UserAccount recordedBy;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "distance_meters", nullable = false, precision = 12, scale = 2)
    private BigDecimal distanceMeters;

    @Column(name = "average_speed_kmh", nullable = false, precision = 8, scale = 2)
    private BigDecimal averageSpeedKmh;

    @Column(name = "training_load", nullable = false, precision = 10, scale = 2)
    private BigDecimal trainingLoad;

    @Column(name = "heart_rate_recovery_bpm", nullable = false)
    private int heartRateRecoveryBpm;

    @Column(name = "perceived_exertion", nullable = false)
    private int perceivedExertion;

    @Enumerated(EnumType.STRING)
    @Column(name = "fitness_status", nullable = false, length = 10)
    private FitnessStatus fitnessStatus;

    @Column(name = "trainer_notes", nullable = false, length = 4000)
    private String trainerNotes;

    @Column(name = "video_url", length = 2000)
    private String videoUrl;

    @Column(name = "completed_at", nullable = false, updatable = false)
    private Instant completedAt;

    protected TrainingEvaluation() {
    }

    public TrainingEvaluation(
            Horse horse,
            UserAccount recordedBy,
            LocalDate sessionDate,
            int durationMinutes,
            BigDecimal distanceMeters,
            BigDecimal averageSpeedKmh,
            BigDecimal trainingLoad,
            int heartRateRecoveryBpm,
            int perceivedExertion,
            FitnessStatus fitnessStatus,
            String trainerNotes,
            String videoUrl,
            Instant completedAt) {
        this(horse, null, recordedBy, sessionDate, durationMinutes, distanceMeters,
                averageSpeedKmh, trainingLoad, heartRateRecoveryBpm, perceivedExertion,
                fitnessStatus, trainerNotes, videoUrl, completedAt);
    }

    public TrainingEvaluation(
            Horse horse,
            TrainingSchedule trainingSchedule,
            UserAccount recordedBy,
            LocalDate sessionDate,
            int durationMinutes,
            BigDecimal distanceMeters,
            BigDecimal averageSpeedKmh,
            BigDecimal trainingLoad,
            int heartRateRecoveryBpm,
            int perceivedExertion,
            FitnessStatus fitnessStatus,
            String trainerNotes,
            String videoUrl,
            Instant completedAt) {
        this.horse = horse;
        this.trainingSchedule = trainingSchedule;
        this.recordedBy = recordedBy;
        this.sessionDate = sessionDate;
        this.durationMinutes = durationMinutes;
        this.distanceMeters = distanceMeters;
        this.averageSpeedKmh = averageSpeedKmh;
        this.trainingLoad = trainingLoad;
        this.heartRateRecoveryBpm = heartRateRecoveryBpm;
        this.perceivedExertion = perceivedExertion;
        this.fitnessStatus = fitnessStatus;
        this.trainerNotes = trainerNotes;
        this.videoUrl = videoUrl;
        this.completedAt = completedAt;
    }

    public Horse getHorse() { return horse; }
    public TrainingSchedule getTrainingSchedule() { return trainingSchedule; }
    public UserAccount getRecordedBy() { return recordedBy; }
    public LocalDate getSessionDate() { return sessionDate; }
    public int getDurationMinutes() { return durationMinutes; }
    public BigDecimal getDistanceMeters() { return distanceMeters; }
    public BigDecimal getAverageSpeedKmh() { return averageSpeedKmh; }
    public BigDecimal getTrainingLoad() { return trainingLoad; }
    public int getHeartRateRecoveryBpm() { return heartRateRecoveryBpm; }
    public int getPerceivedExertion() { return perceivedExertion; }
    public FitnessStatus getFitnessStatus() { return fitnessStatus; }
    public String getTrainerNotes() { return trainerNotes; }
    public String getVideoUrl() { return videoUrl; }
    public Instant getCompletedAt() { return completedAt; }
}
