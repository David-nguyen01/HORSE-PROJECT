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

    @Column(name = "perceived_exertion")
    private Integer perceivedExertion;

    @Enumerated(EnumType.STRING)
    @Column(name = "fitness_status", nullable = false, length = 10)
    private FitnessStatus fitnessStatus;

    @Column(name = "trainer_notes", nullable = false, length = 4000)
    private String trainerNotes;

    @Column(name = "video_url", length = 2000)
    private String videoUrl;

    @Column(name = "completed_at", nullable = false, updatable = false)
    private Instant completedAt;
    @Column(name="actual_run_minutes", precision=8, scale=2) private BigDecimal actualRunMinutes;
    @Column(name="average_heart_rate_bpm") private Integer averageHeartRateBpm;
    @Column(name="max_heart_rate_bpm") private Integer maxHeartRateBpm;
    @Column(name="peak_heart_rate_bpm") private Integer peakHeartRateBpm;
    @Column(name="heart_rate_after_rest_bpm") private Integer heartRateAfterRestBpm;
    @Column(name="rest_minutes", precision=6, scale=2) private BigDecimal restMinutes;
    @Column(name="intensity_percent", precision=5, scale=2) private BigDecimal intensityPercent;
    @Column(name="max_speed_kmh", precision=8, scale=2) private BigDecimal maxSpeedKmh;
    @Column(name="heart_rate_at_stop_bpm") private Integer heartRateAtStopBpm;
    @Column(name="heart_rate_after_1min_bpm") private Integer heartRateAfter1minBpm;
    @Column(name="heart_rate_recovery_rate", precision=9, scale=2) private BigDecimal heartRateRecoveryRate;
    @Column(name="hrr1_bpm") private Integer hrr1Bpm;
    @Column(name="track_completion_seconds", precision=12, scale=3) private BigDecimal trackCompletionSeconds;
    @Column(name="injured") private Boolean injured;
    @Column(name="injury_location", length=200) private String injuryLocation;
    @Column(name="injury_notes", length=4000) private String injuryNotes;

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
            Integer perceivedExertion,
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
            Integer perceivedExertion,
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
    public Integer getPerceivedExertion() { return perceivedExertion; }
    public FitnessStatus getFitnessStatus() { return fitnessStatus; }
    public String getTrainerNotes() { return trainerNotes; }
    public String getVideoUrl() { return videoUrl; }
    public Instant getCompletedAt() { return completedAt; }
    public void setComputedMetrics(BigDecimal runMinutes, Integer averageHr, Integer maxHr, Integer peakHr,
            Integer afterRestHr, BigDecimal restingMinutes, BigDecimal intensity, BigDecimal recoveryRate, Integer hrr1) {
        actualRunMinutes=runMinutes; averageHeartRateBpm=averageHr; maxHeartRateBpm=maxHr; peakHeartRateBpm=peakHr;
        heartRateAfterRestBpm=afterRestHr; restMinutes=restingMinutes; intensityPercent=intensity;
        heartRateRecoveryRate=recoveryRate; hrr1Bpm=hrr1;
    }
    public BigDecimal getActualRunMinutes() { return actualRunMinutes; }
    public Integer getAverageHeartRateBpm() { return averageHeartRateBpm; }
    public Integer getMaxHeartRateBpm() { return maxHeartRateBpm; }
    public Integer getPeakHeartRateBpm() { return peakHeartRateBpm; }
    public Integer getHeartRateAfterRestBpm() { return heartRateAfterRestBpm; }
    public BigDecimal getRestMinutes() { return restMinutes; }
    public BigDecimal getIntensityPercent() { return intensityPercent; }
    public BigDecimal getHeartRateRecoveryRate() { return heartRateRecoveryRate; }
    public Integer getHrr1Bpm() { return hrr1Bpm; }
    public BigDecimal getTrackCompletionSeconds() { return trackCompletionSeconds; }
    public Boolean getInjured() { return injured; }
    public String getInjuryLocation() { return injuryLocation; }
    public String getInjuryNotes() { return injuryNotes; }
    public BigDecimal getMaxSpeedKmh() { return maxSpeedKmh; }
    public Integer getHeartRateAtStopBpm() { return heartRateAtStopBpm; }
    public Integer getHeartRateAfter1minBpm() { return heartRateAfter1minBpm; }
    public void setSessionMetrics(BigDecimal maxSpeed, Integer atStop, Integer afterOneMinute, BigDecimal intensity) {
        maxSpeedKmh=maxSpeed; heartRateAtStopBpm=atStop; heartRateAfter1minBpm=afterOneMinute; intensityPercent=intensity;
    }
    public void setApprovedV3Metrics(BigDecimal trackSeconds, boolean injury, String location, String notes) {
        trackCompletionSeconds = trackSeconds;
        injured = injury;
        injuryLocation = location;
        injuryNotes = notes;
    }
}
