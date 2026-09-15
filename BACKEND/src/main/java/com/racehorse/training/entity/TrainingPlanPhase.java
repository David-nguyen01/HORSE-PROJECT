package com.racehorse.training.entity;

import com.racehorse.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "training_plan_phases")
public class TrainingPlanPhase extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "training_plan_id", nullable = false)
    private TrainingPlan trainingPlan;

    @Column(name = "phase_order", nullable = false)
    private int phaseOrder;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "target_distance_meters", nullable = false, precision = 12, scale = 2)
    private BigDecimal targetDistanceMeters;

    @Enumerated(EnumType.STRING)
    @Column(name = "workload_intensity", nullable = false, length = 10)
    private WorkloadIntensity workloadIntensity;

    @Enumerated(EnumType.STRING)
    @Column(name = "surface_type", nullable = false, length = 20)
    private SurfaceType surfaceType;

    protected TrainingPlanPhase() {
    }

    public TrainingPlanPhase(
            int phaseOrder,
            String name,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal targetDistanceMeters,
            WorkloadIntensity workloadIntensity,
            SurfaceType surfaceType) {
        this.phaseOrder = phaseOrder;
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
        this.targetDistanceMeters = targetDistanceMeters;
        this.workloadIntensity = workloadIntensity;
        this.surfaceType = surfaceType;
    }

    void assignTo(TrainingPlan trainingPlan) { this.trainingPlan = trainingPlan; }

    public TrainingPlan getTrainingPlan() { return trainingPlan; }
    public int getPhaseOrder() { return phaseOrder; }
    public String getName() { return name; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public BigDecimal getTargetDistanceMeters() { return targetDistanceMeters; }
    public WorkloadIntensity getWorkloadIntensity() { return workloadIntensity; }
    public SurfaceType getSurfaceType() { return surfaceType; }
}
