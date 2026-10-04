package com.racehorse.health.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record CreateMedicalExaminationRequest(
        @NotNull @PastOrPresent LocalDate examinationDate,
        @NotBlank @Size(max = 2000) String symptoms,
        @NotBlank @Size(max = 2000) String diagnosis,
        @NotBlank @Size(max = 4000) String treatmentProtocol,
        @Size(max = 2000) String medicationNotes,
        LocalDate followUpDate,
        @Size(max = 30) String healthStatusAfter,
        CreatePreventiveCareRequest preventiveCareSchedule,
        List<CreatePreventiveCareRequest> preventiveSchedules
) {
    public CreateMedicalExaminationRequest(
            LocalDate examinationDate,
            String symptoms,
            String diagnosis,
            String treatmentProtocol,
            String medicationNotes,
            LocalDate followUpDate,
            String healthStatusAfter
    ) {
        this(examinationDate, symptoms, diagnosis, treatmentProtocol, medicationNotes, followUpDate, healthStatusAfter, null, null);
    }

    public CreateMedicalExaminationRequest(
            LocalDate examinationDate,
            String symptoms,
            String diagnosis,
            String treatmentProtocol,
            String medicationNotes,
            LocalDate followUpDate,
            String healthStatusAfter,
            CreatePreventiveCareRequest preventiveCareSchedule
    ) {
        this(examinationDate, symptoms, diagnosis, treatmentProtocol, medicationNotes, followUpDate, healthStatusAfter, preventiveCareSchedule, null);
    }
}
