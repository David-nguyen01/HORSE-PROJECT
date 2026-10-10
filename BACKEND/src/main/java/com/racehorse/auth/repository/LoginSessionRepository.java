package com.racehorse.auth.repository;

import com.racehorse.auth.entity.LoginSession;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface LoginSessionRepository extends JpaRepository<LoginSession, UUID> {

    @EntityGraph(attributePaths = {"user", "user.roles"})
    Optional<LoginSession> findByRefreshTokenHash(String refreshTokenHash);

    boolean existsByIdAndUser_IdAndRevokedAtIsNullAndExpiresAtAfter(UUID id, UUID userId, Instant now);

    @Modifying
    @Query("""
            update LoginSession session
            set session.revokedAt = :now,
                session.lastActivityAt = :now,
                session.updatedAt = :now,
                session.version = session.version + 1
            where session.user.id = :userId
              and session.revokedAt is null
            """)
    int revokeAllActiveByUserId(@Param("userId") UUID userId, @Param("now") Instant now);
}
