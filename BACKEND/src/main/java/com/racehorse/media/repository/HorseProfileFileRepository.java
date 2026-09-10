package com.racehorse.media.repository;

import com.racehorse.media.entity.HorseProfileFile;
import com.racehorse.media.entity.ProfileFileStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HorseProfileFileRepository extends JpaRepository<HorseProfileFile, UUID> {

    @EntityGraph(attributePaths = {"horse", "uploadedBy", "approvedBy"})
    Optional<HorseProfileFile> findFirstByHorseIdAndStatus(UUID horseId, ProfileFileStatus status);

    @EntityGraph(attributePaths = {"horse", "uploadedBy", "approvedBy"})
    List<HorseProfileFile> findByHorseIdAndStatusOrderByCreatedAtAsc(
            UUID horseId,
            ProfileFileStatus status);

    @EntityGraph(attributePaths = {"horse", "uploadedBy", "approvedBy"})
    Optional<HorseProfileFile> findByIdAndHorseIdAndStatus(
            UUID id,
            UUID horseId,
            ProfileFileStatus status);
}
