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

import java.time.Instant;

@Entity
@Table(name = "training_schedules")
public class TrainingSchedule extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "training_plan_phase_id", nullable = false)
    private TrainingPlanPhase phase;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "horse_id", nullable = false)
    private Horse horse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_staff_id")
    private UserAccount assignedStaff;

    @Column(name = "staff_assigned_by", length = 20)
    private String staffAssignedBy;

    @Column(name = "staff_assigned_at")
    private Instant staffAssignedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "racetrack_id")
    private Racetrack racetrack;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "veterinarian_user_id")
    private UserAccount veterinarianUser;

    @Column(name = "vet_assigned_by", length = 20)
    private String vetAssignedBy;

    @Column(name = "vet_assigned_at")
    private Instant vetAssignedAt;

    @Column(name = "task_name", nullable = false, length = 300)
    private String taskName;

    @Enumerated(EnumType.STRING)
    @Column(name = "workload_intensity", nullable = false, length = 10)
    private WorkloadIntensity workloadIntensity;

    @Column(name = "scheduled_start", nullable = false)
    private Instant scheduledStart;

    @Column(name = "scheduled_end", nullable = false)
    private Instant scheduledEnd;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false, updatable = false)
    private UserAccount createdBy;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected TrainingSchedule() {
    }

    public TrainingSchedule(
            TrainingPlanPhase phase,
            Horse horse,
            UserAccount assignedStaff,
            String taskName,
            WorkloadIntensity workloadIntensity,
            Instant scheduledStart,
            Instant scheduledEnd,
            UserAccount createdBy) {
        this.phase = phase;
        this.horse = horse;
        this.assignedStaff = assignedStaff;
        this.taskName = taskName;
        this.workloadIntensity = workloadIntensity;
        this.scheduledStart = scheduledStart;
        this.scheduledEnd = scheduledEnd;
        this.createdBy = createdBy;
    }

    public void publish(Instant time) {
        if (publishedAt == null) publishedAt = time;
    }

    public void complete(Instant time) {
        if (completedAt == null) completedAt = time;
    }

    public void cancel(Instant time) {
        if (cancelledAt == null) cancelledAt = time;
    }

    public TrainingPlanPhase getPhase() { return phase; }
    public Horse getHorse() { return horse; }
    public UserAccount getAssignedStaff() { return assignedStaff; }
    public void setAssignedStaff(UserAccount assignedStaff) { this.assignedStaff = assignedStaff; }
    public String getStaffAssignedBy() { return staffAssignedBy; }
    public void setStaffAssignedBy(String staffAssignedBy) { this.staffAssignedBy = staffAssignedBy; }
    public Instant getStaffAssignedAt() { return staffAssignedAt; }
    public void setStaffAssignedAt(Instant staffAssignedAt) { this.staffAssignedAt = staffAssignedAt; }

    public Racetrack getRacetrack() { return racetrack; }
    public void setRacetrack(Racetrack racetrack) { this.racetrack = racetrack; }

    public UserAccount getVeterinarianUser() { return veterinarianUser; }
    public void setVeterinarianUser(UserAccount veterinarianUser) { this.veterinarianUser = veterinarianUser; }
    public String getVetAssignedBy() { return vetAssignedBy; }
    public void setVetAssignedBy(String vetAssignedBy) { this.vetAssignedBy = vetAssignedBy; }
    public Instant getVetAssignedAt() { return vetAssignedAt; }
    public void setVetAssignedAt(Instant vetAssignedAt) { this.vetAssignedAt = vetAssignedAt; }

    public String getTaskName() { return taskName; }
    public WorkloadIntensity getWorkloadIntensity() { return workloadIntensity; }
    public Instant getScheduledStart() { return scheduledStart; }
    public Instant getScheduledEnd() { return scheduledEnd; }
    public UserAccount getCreatedBy() { return createdBy; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public Instant getCompletedAt() { return completedAt; }
}
