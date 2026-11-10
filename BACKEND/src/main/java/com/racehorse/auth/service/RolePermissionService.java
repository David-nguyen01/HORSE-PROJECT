package com.racehorse.auth.service;

import com.racehorse.auth.dto.RolePermissionResponse;
import com.racehorse.auth.dto.UpdateRolePermissionRequest;
import com.racehorse.auth.entity.Role;
import com.racehorse.common.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import java.util.UUID;

@Service
public class RolePermissionService {
    private static final Map<String, Set<String>> SUPPORTED_ACTIONS = Map.of(
            "PROFILE", Set.of("VIEW", "EDIT", "APPROVE", "CLAIM"),
            "TRAINING", Set.of("VIEW", "EDIT", "APPROVE", "CLAIM"),
            "MEDICAL", Set.of("VIEW", "EDIT", "APPROVE", "CLAIM"),
            "STABLE_CARE", Set.of("VIEW", "EDIT", "APPROVE", "CLAIM"),
            "RACING", Set.of("VIEW", "EDIT", "APPROVE", "CLAIM"),
            "REPORTING", Set.of("VIEW", "EDIT", "APPROVE", "CLAIM"),
            "INVENTORY", Set.of("VIEW", "EDIT", "APPROVE", "CLAIM"),
            "SYSTEM", Set.of("VIEW", "EDIT", "APPROVE", "CLAIM"));
    private final JdbcTemplate jdbc;

    public RolePermissionService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public List<RolePermissionResponse> matrix() {
        return jdbc.query("""
                with roles(code) as (
                    select code from (values ('CLUB_MANAGER'),('HEAD_TRAINER'),('VETERINARIAN'),('STABLE_STAFF'),('HORSE_OWNER')) r(code)
                ), modules(module_code) as (
                    select m from (values ('PROFILE'),('TRAINING'),('MEDICAL'),('STABLE_CARE'),('RACING'),('REPORTING'),('INVENTORY'),('SYSTEM')) m(m)
                ), actions(action_code) as (
                    select a from (values ('VIEW'),('EDIT'),('APPROVE'),('CLAIM')) a(a)
                ), permissions(module_code,action_code) as (
                    select module_code, action_code from modules cross join actions
                )
                select r.code,p.module_code,p.action_code,rp.granted
                from roles r cross join permissions p
                left join role_permissions rp on rp.role_code=r.code
                    and rp.module_code=p.module_code and rp.action_code=p.action_code
                order by r.code,p.module_code,p.action_code
                """, (rs, row) -> new RolePermissionResponse(rs.getString(1), rs.getString(2), rs.getString(3), rs.getBoolean(4)));
    }

    @Transactional
    public RolePermissionResponse update(UUID actorId, Role role, UpdateRolePermissionRequest request) {
        if (role == Role.CLUB_MANAGER) {
            throw new BusinessException("Không được hạ quyền của CLUB_MANAGER để tránh khóa hệ thống");
        }
        String module = request.module().trim().toUpperCase(Locale.ROOT);
        String action = request.action().trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_ACTIONS.getOrDefault(module, Set.of()).contains(action)) {
            throw new BusinessException("Quyền này không được backend hỗ trợ cho phân hệ đã chọn");
        }
        OffsetDateTime changedAt = OffsetDateTime.now();
        int changed = jdbc.update("""
                update role_permissions
                set granted=?, changed_by_user_id=?, changed_at=?
                where role_code=? and module_code=? and action_code=?
                """, request.granted(), actorId, changedAt, role.name(), module, action);
        if (changed == 0) {
            jdbc.update("""
                    insert into role_permissions(role_code,module_code,action_code,granted,changed_by_user_id,changed_at)
                    values(?,?,?,?,?,?)
                    """, role.name(), module, action, request.granted(), actorId, changedAt);
        }
        return new RolePermissionResponse(role.name(), module, action, request.granted());
    }
}
