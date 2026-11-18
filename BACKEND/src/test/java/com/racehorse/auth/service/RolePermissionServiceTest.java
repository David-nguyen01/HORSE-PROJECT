package com.racehorse.auth.service;

import com.racehorse.auth.dto.RolePermissionResponse;
import com.racehorse.auth.dto.UpdateRolePermissionRequest;
import com.racehorse.auth.entity.Role;
import com.racehorse.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RolePermissionServiceTest {
    @Mock private JdbcTemplate jdbc;
    private RolePermissionService service;
    private final UUID actor = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new RolePermissionService(jdbc);
    }

    @Test
    @SuppressWarnings("unchecked")
    void matrix_queriesAllModulesAndActions() {
        when(jdbc.query(contains("INVENTORY"), any(RowMapper.class)))
                .thenReturn(List.of(new RolePermissionResponse("HEAD_TRAINER", "INVENTORY", "VIEW", true)));

        List<RolePermissionResponse> result = service.matrix();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().module()).isEqualTo("INVENTORY");
        verify(jdbc).query(contains("modules(module_code)"), any(RowMapper.class));
    }

    @Test
    void update_cannotDowngradeClubManager() {
        UpdateRolePermissionRequest request = new UpdateRolePermissionRequest("PROFILE", "VIEW", false);

        assertThatThrownBy(() -> service.update(actor, Role.CLUB_MANAGER, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Không được hạ quyền của CLUB_MANAGER");

        verifyNoInteractions(jdbc);
    }

    @Test
    void update_inventoryClaimPermission_succeeds() {
        when(jdbc.update(contains("update role_permissions"), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        UpdateRolePermissionRequest request = new UpdateRolePermissionRequest("INVENTORY", "CLAIM", true);
        RolePermissionResponse response = service.update(actor, Role.STABLE_STAFF, request);

        assertThat(response).isNotNull();
        assertThat(response.module()).isEqualTo("INVENTORY");
        assertThat(response.action()).isEqualTo("CLAIM");
        assertThat(response.granted()).isTrue();
    }

    @Test
    void update_systemViewPermission_isRejected() {
        UpdateRolePermissionRequest request = new UpdateRolePermissionRequest("SYSTEM", "VIEW", true);
        assertThatThrownBy(() -> service.update(actor, Role.HEAD_TRAINER, request))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(jdbc);
    }

    @Test
    void update_unsupportedModule_throwsException() {
        UpdateRolePermissionRequest request = new UpdateRolePermissionRequest("NON_EXISTING", "VIEW", true);

        assertThatThrownBy(() -> service.update(actor, Role.HEAD_TRAINER, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không được backend hỗ trợ");

        verifyNoInteractions(jdbc);
    }

    @Test
    void update_insertsPermissionWhenItIsMissing() {
        when(jdbc.update(contains("update role_permissions"), any(), any(), any(), any(), any(), any()))
                .thenReturn(0);
        when(jdbc.update(contains("insert into role_permissions"), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        RolePermissionResponse response = service.update(
                actor, Role.VETERINARIAN, new UpdateRolePermissionRequest("MEDICAL", "EDIT", true));

        assertThat(response.granted()).isTrue();
        verify(jdbc).update(contains("insert into role_permissions"),
                eq("VETERINARIAN"), eq("MEDICAL"), eq("EDIT"), eq(true), eq(actor), any());
    }

    @Test
    void cannotGrantVeterinaryEditingToTrainer() {
        assertThatThrownBy(() -> service.update(actor, Role.HEAD_TRAINER,
                new UpdateRolePermissionRequest("MEDICAL", "EDIT", true)))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(jdbc);
        assertThat(RolePermissionService.supports(Role.HEAD_TRAINER, "PERM_MEDICAL_EDIT")).isFalse();
        assertThat(RolePermissionService.supports(Role.VETERINARIAN, "PERM_MEDICAL_EDIT")).isTrue();
    }

    @Test
    void preservesManagerProfileApprovalWithoutGrantingItToOtherRoles() {
        assertThat(RolePermissionService.supports(Role.CLUB_MANAGER, "PERM_PROFILE_APPROVE")).isTrue();
        assertThat(RolePermissionService.supports(Role.HEAD_TRAINER, "PERM_PROFILE_APPROVE")).isFalse();
        assertThat(RolePermissionService.supports(Role.HORSE_OWNER, "PERM_PROFILE_APPROVE")).isFalse();
    }
}
