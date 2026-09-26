package com.racehorse.auth.repository;

import com.racehorse.auth.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByUsernameIgnoreCase(String username);

    Optional<UserAccount> findByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    @Query("""
            select u from UserAccount u
            where (:query is null
                   or lower(u.username) like lower(concat('%', :query, '%'))
                   or lower(u.email) like lower(concat('%', :query, '%')))
            """
            )
    Page<UserAccount> search(@Param("query") String query, Pageable pageable);

    @Query(value = """
            select concat('PERM_', rp.module_code, '_', rp.action_code)
            from user_roles ur
            join role_permissions rp on rp.role_code = ur.role and rp.granted = 1
            where ur.user_id = :userId
            """, nativeQuery = true)
    List<String> findGrantedPermissionCodes(@Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select distinct u from UserAccount u join u.roles role
            where role = com.racehorse.auth.entity.Role.CLUB_MANAGER
              and u.enabled = true
            """)
    List<UserAccount> findActiveClubManagersForUpdate();
}
