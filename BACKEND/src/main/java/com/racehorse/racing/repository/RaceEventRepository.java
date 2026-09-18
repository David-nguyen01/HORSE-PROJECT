package com.racehorse.racing.repository;

import com.racehorse.racing.entity.RaceEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RaceEventRepository extends JpaRepository<RaceEvent, UUID> {
    
    @EntityGraph(attributePaths = {"createdBy"})
    @Query("""
            select r from RaceEvent r
            where r.active = true
            order by r.raceDate asc, r.registrationDeadline asc
            """)
    Page<RaceEvent> findUpcoming(Pageable pageable);

    @EntityGraph(attributePaths = {"createdBy"})
    Optional<RaceEvent> findDetailById(UUID id);
}
