package com.racehorse.training.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "racetracks")
public class Racetrack {
    @Id @GeneratedValue @UuidGenerator
    private UUID id;
    @Column(nullable = false, unique = true, length = 200)
    private String name;
    @Column(length = 300)
    private String location;
    @Column(name = "is_active", nullable = false)
    private boolean active = true;
    @Column(name = "opens_at", nullable = false)
    private LocalTime opensAt = LocalTime.of(5, 0);
    @Column(name = "closes_at", nullable = false)
    private LocalTime closesAt = LocalTime.of(11, 0);
    @Column(name = "closes_next_day", nullable = false)
    private boolean closesNextDay = false;
    @Column(name = "slot_minutes", nullable = false)
    private int slotMinutes = 360;

    protected Racetrack() {}
    public Racetrack(String name, String location) { this.name = name; this.location = location; }
    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getLocation() { return location; }
    public boolean isActive() { return active; }
    public LocalTime getOpensAt() { return opensAt; }
    public LocalTime getClosesAt() { return closesAt; }
    public boolean isClosesNextDay() { return closesNextDay; }
    public int getSlotMinutes() { return slotMinutes; }
}
