package com.racehorse.training.entity;

import com.racehorse.auth.entity.UserAccount;
import com.racehorse.common.persistence.BaseEntity;
import com.racehorse.horse.entity.Horse;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "training_plans")
public class TrainingPlan extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "horse_id", nullable = false)
    private Horse horse;

    @Column(nullable = false, length = 200)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_plan_id")
    private TrainingPlan sourcePlan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false, updatable = false)
    private UserAccount createdBy;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @OneToMany(mappedBy = "trainingPlan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("phaseOrder ASC")
    private List<TrainingPlanPhase> phases = new ArrayList<>();

    protected TrainingPlan() {
    }

    public TrainingPlan(Horse horse, String name, TrainingPlan sourcePlan, UserAccount createdBy, boolean active) {
        this.horse = horse;
        this.name = name;
        this.sourcePlan = sourcePlan;
        this.createdBy = createdBy;
        this.active = active;
    }

    public void update(String name, boolean active) {
        this.name = name;
        this.active = active;
    }

    public void replacePhases(List<TrainingPlanPhase> replacement) {
        phases.clear();
        replacement.forEach(phase -> {
            phase.assignTo(this);
            phases.add(phase);
        });
    }

    public void updatePhases(List<TrainingPlanPhase> replacement) {
        Set<UUID> retainedIds = replacement.stream().map(BaseEntity::getId).filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        phases.removeIf(phase -> phase.getId() != null && !retainedIds.contains(phase.getId()));
        for (TrainingPlanPhase phase : replacement) {
            if (!phases.contains(phase)) {
                phase.assignTo(this);
                phases.add(phase);
            }
        }
        phases.sort(Comparator.comparingInt(TrainingPlanPhase::getPhaseOrder));
    }

    public Horse getHorse() { return horse; }
    public String getName() { return name; }
    public TrainingPlan getSourcePlan() { return sourcePlan; }
    public UserAccount getCreatedBy() { return createdBy; }
    public boolean isActive() { return active; }
    public List<TrainingPlanPhase> getPhases() { return Collections.unmodifiableList(phases); }
}
