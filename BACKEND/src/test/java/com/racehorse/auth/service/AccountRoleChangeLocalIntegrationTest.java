package com.racehorse.auth.service;

import com.racehorse.auth.dto.UpdateUserRolesRequest;
import com.racehorse.auth.entity.Role;
import com.racehorse.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named = "RUN_LOCAL_DB_INTEGRATION", matches = "true")
class AccountRoleChangeLocalIntegrationTest {
    @Autowired UserManagementService users;
    @Autowired JdbcTemplate jdbc;

    @Test
    @Transactional
    void ownerWithHorseCannotChangeRole() {
        UUID ownerId = jdbc.queryForObject("""
                select top 1 o.owner_id from dbo.horse_ownerships o
                join dbo.user_roles r on r.user_id=o.owner_id and r.role='HORSE_OWNER'
                """, UUID.class);
        UUID managerId = jdbc.queryForObject("""
                select top 1 user_id from dbo.user_roles where role='CLUB_MANAGER'
                """, UUID.class);

        assertThatThrownBy(() -> users.updateRoles(managerId, ownerId,
                new UpdateUserRolesRequest(Set.of(Role.HEAD_TRAINER))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("sở hữu ngựa");
    }
}
