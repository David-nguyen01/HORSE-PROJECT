package com.racehorse.training.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CopyTrainingPlanResponse(TrainingPlanResponse plan, List<PhaseRestDays> phaseRestDays) {
    public record PhaseRestDays(UUID phaseId, String phaseName, List<LocalDate> kept, List<DroppedRestDay> dropped) {}
    public record DroppedRestDay(LocalDate date, String reason) {}
}
