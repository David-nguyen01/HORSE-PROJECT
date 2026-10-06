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
import java.util.UUID;

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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "racetrack_id", nullable = false)
    private Racetrack racetrack;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "veterinarian_user_id")
    private UserAccount veterinarian;

    @Column(name = "staff_assigned_by", length = 20)
    private String staffAssignedBy;
    @Column(name = "staff_assigned_at") private Instant staffAssignedAt;
    @Column(name = "vet_assigned_by", length = 20) private String vetAssignedBy;
    @Column(name = "vet_assigned_at") private Instant vetAssignedAt;

    @Column(name = "task_name", nullable = false, length = 300)
    private String taskName;

    @Column(name = "notes", length = 2000)
    private String notes;

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

    @Column(name = "session_status", length = 20)
    private String sessionStatus = "PREPARING";

    @Column(name = "ready_at")
    private Instant readyAt;

    @Column(name = "ready_by_user_id")
    private UUID readyByUserId;

    @Column(name = "started_at")
    private Instant startedAt;

    protected TrainingSchedule() {
    }

    public TrainingSchedule(
            TrainingPlanPhase phase,
            Horse horse,
            Racetrack racetrack,
            String taskName,
            WorkloadIntensity workloadIntensity,
            Instant scheduledStart,
            Instant scheduledEnd,
            UserAccount createdBy) {
        this.phase = phase;
        this.horse = horse;
        this.racetrack = racetrack;
        this.taskName = taskName;
        this.workloadIntensity = workloadIntensity;
        this.scheduledStart = scheduledStart;
        this.scheduledEnd = scheduledEnd;
        this.createdBy = createdBy;
    }

    /** Compatibility constructor for historical fixtures. New sessions must provide a training ground. */
    public TrainingSchedule(TrainingPlanPhase phase, Horse horse, UserAccount assignedStaff, String taskName,
            WorkloadIntensity workloadIntensity, Instant scheduledStart, Instant scheduledEnd, UserAccount createdBy) {
        this(phase, horse, (Racetrack) null, taskName, workloadIntensity, scheduledStart, scheduledEnd, createdBy);
        this.assignedStaff = assignedStaff;
    }

    public void publish(Instant time) {
        if (publishedAt == null) publishedAt = time;
    }

    public void complete(Instant time) {
        if (completedAt == null) completedAt = time;
    }

    public void markReady(UUID userId, Instant time) {
        sessionStatus = "READY";
        readyByUserId = userId;
        readyAt = time;
    }

    public void startTraining(Instant time) {
        sessionStatus = "IN_PROGRESS";
        startedAt = time;
    }

    public void finishTraining(Instant time) {
        sessionStatus = "COMPLETED";
        completedAt = time;
    }
    public void updateDetails(Racetrack track, String name, Instant start, Instant end) {
        racetrack = track;
        taskName = name;
        scheduledStart = start;
        scheduledEnd = end;
    }

    public void updateEditableFields(Racetrack track, String name, String sessionNotes,
            WorkloadIntensity intensity, Instant start, Instant end) {
        racetrack = track;
        taskName = name;
        notes = sessionNotes;
        if (intensity != null) workloadIntensity = intensity;
        scheduledStart = start;
        scheduledEnd = end;
    }

    public void clearStaff() {
        assignedStaff = null;
        staffAssignedBy = null;
        staffAssignedAt = null;
    }

    public void clearVeterinarian() {
        veterinarian = null;
        vetAssignedBy = null;
        vetAssignedAt = null;
    }

    public void assignStaff(UserAccount value, String by, Instant at) { assignedStaff=value; staffAssignedBy=by; staffAssignedAt=at; }
    public void assignVeterinarian(UserAccount value, String by, Instant at) { veterinarian=value; vetAssignedBy=by; vetAssignedAt=at; }

    public void cancel(Instant time) {
        if (cancelledAt == null) cancelledAt = time;
    }

    public void releaseAssignments() {
        assignedStaff = null;
        staffAssignedBy = null;
        staffAssignedAt = null;
        veterinarian = null;
        vetAssignedBy = null;
        vetAssignedAt = null;
    }

    public TrainingPlanPhase getPhase() { return phase; }
    public Horse getHorse() { return horse; }
    public UserAccount getAssignedStaff() { return assignedStaff; }
    public Racetrack getRacetrack() { return racetrack; }
    public UserAccount getVeterinarian() { return veterinarian; }
    public String getStaffAssignedBy() { return staffAssignedBy; }
    public String getVetAssignedBy() { return vetAssignedBy; }
    public String getTaskName() { return taskName; }
    public String getNotes() { return notes; }
    public WorkloadIntensity getWorkloadIntensity() { return workloadIntensity; }
    public Instant getScheduledStart() { return scheduledStart; }
    public Instant getScheduledEnd() { return scheduledEnd; }
    public UserAccount getCreatedBy() { return createdBy; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public Instant getCompletedAt() { return completedAt; }
    public String getSessionStatus() { return sessionStatus; }
    public Instant getReadyAt() { return readyAt; }
    public UUID getReadyByUserId() { return readyByUserId; }
    public Instant getStartedAt() { return startedAt; }
}
