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
            "PROFILE", Set.of("VIEW", "EDIT", "APPROVE"),
            "TRAINING", Set.of("VIEW", "EDIT"),
            "MEDICAL", Set.of("VIEW", "EDIT"),
            "STABLE_CARE", Set.of("VIEW", "EDIT"),
            "RACING", Set.of("VIEW", "EDIT", "APPROVE"),
            "REPORTING", Set.of("VIEW", "EDIT"));
    private final JdbcTemplate jdbc;

    public RolePermissionService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public List<RolePermissionResponse> matrix() {
        return jdbc.query("""
                with roles(code) as (
                    select code from (values ('CLUB_MANAGER'),('HEAD_TRAINER'),('VETERINARIAN'),('STABLE_STAFF'),('HORSE_OWNER')) r(code)
                ), permissions(module_code,action_code) as (
                    select * from (values
                        ('PROFILE','VIEW'),('PROFILE','EDIT'),('PROFILE','APPROVE'),
                        ('TRAINING','VIEW'),('TRAINING','EDIT'),
                        ('MEDICAL','VIEW'),('MEDICAL','EDIT'),
                        ('STABLE_CARE','VIEW'),('STABLE_CARE','EDIT'),
                        ('RACING','VIEW'),('RACING','EDIT'),('RACING','APPROVE'),
                        ('REPORTING','VIEW'),('REPORTING','EDIT')) p(module_code,action_code)
                )
                select r.code,p.module_code,p.action_code,coalesce(rp.granted,0)
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
        int changed = jdbc.update("""
                merge role_permissions as target
                using (select ? role_code,? module_code,? action_code) source
                on target.role_code=source.role_code and target.module_code=source.module_code and target.action_code=source.action_code
                when matched then update set granted=?,changed_by_user_id=?,changed_at=?
                when not matched then insert(role_code,module_code,action_code,granted,changed_by_user_id,changed_at)
                    values(source.role_code,source.module_code,source.action_code,?,?,?);
                """, role.name(), module, action, request.granted(), actorId, OffsetDateTime.now(),
                request.granted(), actorId, OffsetDateTime.now());
        if (changed == 0) throw new BusinessException("Không tìm thấy role hoặc quyền cần sửa");
        return new RolePermissionResponse(role.name(), module, action, request.granted());
    }
}
