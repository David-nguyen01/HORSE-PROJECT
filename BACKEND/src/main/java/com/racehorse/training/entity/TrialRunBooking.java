package com.racehorse.training.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trial_run_bookings")
public class TrialRunBooking {
    @Id @GeneratedValue @UuidGenerator
    private UUID id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "training_schedule_id", nullable = false, unique = true)
    private TrainingSchedule trainingSchedule;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "racetrack_lane_id", nullable = false)
    private RacetrackLane lane;
    @Column(name = "booked_start", nullable = false)
    private Instant bookedStart;
    @Column(name = "booked_end", nullable = false)
    private Instant bookedEnd;
    @Column(name = "cancelled_at")
    private Instant cancelledAt;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TrialRunBooking() {}
    public TrialRunBooking(TrainingSchedule schedule, RacetrackLane lane, Instant start, Instant end) {
        this.trainingSchedule = schedule; this.lane = lane; this.bookedStart = start; this.bookedEnd = end;
    }
    public UUID getId() { return id; }
    public TrainingSchedule getTrainingSchedule() { return trainingSchedule; }
    public RacetrackLane getLane() { return lane; }
    public Instant getBookedStart() { return bookedStart; }
    public Instant getBookedEnd() { return bookedEnd; }
    public Instant getCancelledAt() { return cancelledAt; }
    public void cancel(Instant at) {
        if (cancelledAt == null) cancelledAt = at;
    }
    public void reschedule(RacetrackLane value, Instant start, Instant end) {
        lane = value;
        bookedStart = start;
        bookedEnd = end;
        cancelledAt = null;
    }
}
