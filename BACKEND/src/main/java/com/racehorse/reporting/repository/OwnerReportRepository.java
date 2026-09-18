package com.racehorse.reporting.repository;

import com.racehorse.reporting.entity.OwnerReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.UUID;

public interface OwnerReportRepository extends JpaRepository<OwnerReport, UUID> {
    
    @Query("""
            select r from OwnerReport r
            where (:ownerId is null or r.owner.id = :ownerId)
              and (:startDate is null or r.periodEnd >= :startDate)
              and (:endDate is null or r.periodStart <= :endDate)
            order by r.periodEnd desc, r.generatedAt desc
            """)
    Page<OwnerReport> findReports(
            @Param("ownerId") UUID ownerId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable);
}
