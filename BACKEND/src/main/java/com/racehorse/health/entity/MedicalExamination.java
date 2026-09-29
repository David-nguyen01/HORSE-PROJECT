package com.racehorse.health.entity;

import com.racehorse.auth.entity.UserAccount;
import com.racehorse.horse.entity.Horse;
import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "medical_examinations")
public class MedicalExamination {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "horse_id", nullable = false)
    private Horse horse;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "veterinarian_user_id", nullable = false)
    private UserAccount veterinarian;

    @Column(name = "examination_date", nullable = false)
    private LocalDate examinationDate;

    @Column(nullable = false, length = 4000)
    private String symptoms;

    @Column(nullable = false, length = 4000)
    private String diagnosis;

    @Column(name = "treatment_plan", nullable = false, length = 4000)
    private String treatmentPlan;

    @Column(name = "medication_and_dosage", length = 4000)
    private String medicationAndDosage;

    @Column(name = "follow_up_date")
    private LocalDate followUpDate;

    @Column(name = "health_status_after", nullable = false, length = 20)
    private String healthStatusAfter;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    protected MedicalExamination() {}

    public MedicalExamination(Horse horse, UserAccount veterinarian, LocalDate examinationDate,
                               String symptoms, String diagnosis, String treatmentPlan,
                               String medicationAndDosage, LocalDate followUpDate,
                               String healthStatusAfter, Instant submittedAt) {
        this.horse = horse;
        this.veterinarian = veterinarian;
        this.examinationDate = examinationDate;
        this.symptoms = symptoms;
        this.diagnosis = diagnosis;
        this.treatmentPlan = treatmentPlan;
        this.medicationAndDosage = medicationAndDosage;
        this.followUpDate = followUpDate;
        this.healthStatusAfter = healthStatusAfter;
        this.submittedAt = submittedAt;
    }

    public UUID getId() { return id; }
    public Horse getHorse() { return horse; }
    public UserAccount getVeterinarian() { return veterinarian; }
    public LocalDate getExaminationDate() { return examinationDate; }
    public String getSymptoms() { return symptoms; }
    public String getDiagnosis() { return diagnosis; }
    public String getTreatmentPlan() { return treatmentPlan; }
    public String getMedicationAndDosage() { return medicationAndDosage; }
    public LocalDate getFollowUpDate() { return followUpDate; }
    public String getHealthStatusAfter() { return healthStatusAfter; }
    public Instant getSubmittedAt() { return submittedAt; }
}
