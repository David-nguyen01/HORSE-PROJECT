package com.racehorse.audit.repository;

import com.racehorse.audit.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @EntityGraph(attributePaths = {"actor"})
    @Query("""
            select a from AuditLog a
            where (:moduleCode is null or a.moduleCode = :moduleCode)
              and (:actionCode is null or a.actionCode = :actionCode)
              and (:startDate is null or a.occurredAt >= :startDate)
              and (:endDate is null or a.occurredAt <= :endDate)
              and (:query is null or lower(a.description) like lower(concat('%', :query, '%')))
            """)
    Page<AuditLog> search(
            @Param("moduleCode") String moduleCode,
            @Param("actionCode") String actionCode,
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate,
            @Param("query") String query,
            Pageable pageable);
}
