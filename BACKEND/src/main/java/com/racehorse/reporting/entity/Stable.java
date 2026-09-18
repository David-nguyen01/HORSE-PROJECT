package com.racehorse.reporting.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "stables")
public class Stable {

    @Id
    private UUID id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 300)
    private String location;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    protected Stable() {}

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getLocation() { return location; }
    public boolean isActive() { return active; }
}
