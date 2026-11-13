package com.racehorse.servicepackage.repository;

import com.racehorse.servicepackage.entity.ServicePackageEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ServicePackageRepository extends JpaRepository<ServicePackageEntity, UUID> {

    Optional<ServicePackageEntity> findByCode(String code);

    boolean existsByCode(String code);

    List<ServicePackageEntity> findByIsActiveOrderByDisplayOrderAscCreatedAtAsc(boolean isActive);

    List<ServicePackageEntity> findAllByOrderByDisplayOrderAscCreatedAtAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ServicePackageEntity p where p.id = :id")
    Optional<ServicePackageEntity> findByIdForUpdate(@Param("id") UUID id);
}
