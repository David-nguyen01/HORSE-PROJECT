package com.racehorse.health.repository;

import com.racehorse.health.entity.MedicalExamination;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MedicalExaminationRepository extends JpaRepository<MedicalExamination, UUID> {

    @EntityGraph(attributePaths = {"horse", "veterinarian"})
    Page<MedicalExamination> findByHorseIdOrderByExaminationDateDescSubmittedAtDesc(UUID horseId, Pageable pageable);

    @EntityGraph(attributePaths = {"horse", "veterinarian"})
    Optional<MedicalExamination> findWithDetailsById(UUID id);
}
