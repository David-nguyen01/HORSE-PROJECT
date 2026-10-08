package com.racehorse.training.service;

import com.racehorse.training.entity.FitnessStatus;

public final class FitnessStatusPolicy {
    public static final Thresholds THRESHOLDS = new Thresholds(120, 140);

    private FitnessStatusPolicy() {}

    public static FitnessStatus fromOneMinuteBpm(int bpm) {
        if (bpm < THRESHOLDS.greenBelow()) return FitnessStatus.GREEN;
        if (bpm <= THRESHOLDS.yellowThrough()) return FitnessStatus.YELLOW;
        return FitnessStatus.RED;
    }

    public record Thresholds(int greenBelow, int yellowThrough) {}
}
