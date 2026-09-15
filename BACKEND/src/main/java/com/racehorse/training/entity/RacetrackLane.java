package com.racehorse.training.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;
import java.util.UUID;

@Entity
@Table(name = "racetrack_lanes")
public class RacetrackLane {
    @Id @GeneratedValue @UuidGenerator
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "racetrack_id", nullable = false)
    private Racetrack racetrack;
    @Column(name = "lane_number", nullable = false)
    private int laneNumber;
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    protected RacetrackLane() {}
    public RacetrackLane(Racetrack racetrack, int laneNumber) { this.racetrack = racetrack; this.laneNumber = laneNumber; }
    public UUID getId() { return id; }
    public Racetrack getRacetrack() { return racetrack; }
    public int getLaneNumber() { return laneNumber; }
    public boolean isActive() { return active; }
}
