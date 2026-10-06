package com.racehorse.horse.repository;

import com.racehorse.horse.entity.Horse;
import com.racehorse.horse.entity.HorseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface HorseRepository extends JpaRepository<Horse, UUID> {

    List<Horse> findByArchivedAtIsNullOrderByNameAsc();

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByMicrochipPassportNumberIgnoreCase(String microchipPassportNumber);

    boolean existsByMicrochipPassportNumberIgnoreCaseAndIdNot(
            String microchipPassportNumber,
            UUID id);

    @EntityGraph(attributePaths = {"sire", "dam", "currentTrainer"})
    @Query("""
            select h from Horse h
            where h.id = :id
            """)
    Optional<Horse> findDetailById(@Param("id") UUID id);

    @Query("""
            select h from Horse h
            where h.archivedAt is null
              and (:query is null
                   or lower(h.code) like lower(concat('%', :query, '%'))
                   or lower(h.name) like lower(concat('%', :query, '%')))
              and (:status is null or h.status = :status)
            """)
    Page<Horse> search(
            @Param("query") String query,
            @Param("status") HorseStatus status,
            Pageable pageable);

    @Query("""
            select h from Horse h
            where h.archivedAt is null and h.currentTrainer.id = :trainerId
              and (:query is null or lower(h.code) like lower(concat('%', :query, '%'))
                   or lower(h.name) like lower(concat('%', :query, '%')))
              and (:status is null or h.status = :status)
            """)
    Page<Horse> searchForTrainer(@Param("query") String query, @Param("status") HorseStatus status,
                                @Param("trainerId") UUID trainerId, Pageable pageable);

    @EntityGraph(attributePaths = {"sire", "dam", "currentTrainer"})
    @Query("""
            select distinct h from Horse h
            join HorseOwnership ho on ho.horse = h
            where ho.owner.id = :ownerId
              and h.archivedAt is null
            """)
    Page<Horse> findOwnedBy(
            @Param("ownerId") UUID ownerId,
            Pageable pageable);
}
