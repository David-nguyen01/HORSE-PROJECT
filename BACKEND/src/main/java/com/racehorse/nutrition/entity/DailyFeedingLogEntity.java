package com.racehorse.nutrition.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "daily_feeding_logs", schema = "dbo")
public class DailyFeedingLogEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "horse_id", nullable = false)
    private UUID horseId;

    @Column(name = "feeding_date", nullable = false)
    private LocalDate feedingDate;

    @Column(name = "meal_key", nullable = false, length = 100)
    private String mealKey;

    @Column(name = "meal_name", nullable = false, length = 100)
    private String mealName;

    @Column(name = "feeding_time", nullable = false)
    private LocalTime feedingTime;

    @Column(name = "plan_id")
    private UUID planId;

    @Column(name = "plan_version", nullable = false)
    private int planVersion;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "CONFIRMED";

    @Column(name = "confirmed_by_user_id")
    private UUID confirmedByUserId;

    @Column(name = "confirmed_at", nullable = false)
    private OffsetDateTime confirmedAt;

    @Column(name = "meal_snapshot_json", columnDefinition = "text", nullable = false)
    private String mealSnapshotJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public DailyFeedingLogEntity() {}

    public DailyFeedingLogEntity(UUID id, UUID horseId, LocalDate feedingDate, String mealKey,
                                 String mealName, LocalTime feedingTime, UUID planId,
                                 int planVersion, String status, UUID confirmedByUserId,
                                 OffsetDateTime confirmedAt, String mealSnapshotJson,
                                 OffsetDateTime createdAt) {
        this.id = id;
        this.horseId = horseId;
        this.feedingDate = feedingDate;
        this.mealKey = mealKey;
        this.mealName = mealName;
        this.feedingTime = feedingTime;
        this.planId = planId;
        this.planVersion = planVersion;
        this.status = status;
        this.confirmedByUserId = confirmedByUserId;
        this.confirmedAt = confirmedAt;
        this.mealSnapshotJson = mealSnapshotJson;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getHorseId() {
        return horseId;
    }

    public void setHorseId(UUID horseId) {
        this.horseId = horseId;
    }

    public LocalDate getFeedingDate() {
        return feedingDate;
    }

    public void setFeedingDate(LocalDate feedingDate) {
        this.feedingDate = feedingDate;
    }

    public String getMealKey() {
        return mealKey;
    }

    public void setMealKey(String mealKey) {
        this.mealKey = mealKey;
    }

    public String getMealName() {
        return mealName;
    }

    public void setMealName(String mealName) {
        this.mealName = mealName;
    }

    public LocalTime getFeedingTime() {
        return feedingTime;
    }

    public void setFeedingTime(LocalTime feedingTime) {
        this.feedingTime = feedingTime;
    }

    public UUID getPlanId() {
        return planId;
    }

    public void setPlanId(UUID planId) {
        this.planId = planId;
    }

    public int getPlanVersion() {
        return planVersion;
    }

    public void setPlanVersion(int planVersion) {
        this.planVersion = planVersion;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public UUID getConfirmedByUserId() {
        return confirmedByUserId;
    }

    public void setConfirmedByUserId(UUID confirmedByUserId) {
        this.confirmedByUserId = confirmedByUserId;
    }

    public OffsetDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(OffsetDateTime confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public String getMealSnapshotJson() {
        return mealSnapshotJson;
    }

    public void setMealSnapshotJson(String mealSnapshotJson) {
        this.mealSnapshotJson = mealSnapshotJson;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
