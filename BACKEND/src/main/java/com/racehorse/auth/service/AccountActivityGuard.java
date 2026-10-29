package com.racehorse.auth.service;

import com.racehorse.common.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/** Keeps role changes from invalidating existing business ownership and attribution. */
@Service
public class AccountActivityGuard {
    private final JdbcTemplate jdbc;

    private record Blocker(String description, String sql) {}

    // Login sessions and notifications alone do not count as business activity.
    private static final List<Blocker> ROLE_CHANGE_BLOCKERS = List.of(
            new Blocker("đang hoặc từng sở hữu ngựa", "from dbo.horse_ownerships where owner_id=?"),
            new Blocker("đã được phân công hoặc ghi chỉ số cho ngựa", "from dbo.horses where ? in (current_trainer_id, caretaker_user_id, diagnosing_vet_user_id, health_changed_by_user_id, weight_measured_by_user_id)"),
            new Blocker("đã tải tệp lên", "from dbo.stored_files where uploaded_by_user_id=?"),
            new Blocker("đã xử lý tệp hồ sơ ngựa", "from dbo.horse_profile_files where ? in (uploaded_by_id, approved_by_id)"),
            new Blocker("đã lập giáo án", "from dbo.training_plans where created_by_user_id=?"),
            new Blocker("đã lập hoặc xác nhận lịch tập", "from dbo.training_schedules where ? in (created_by_user_id, ready_by_user_id)"),
            new Blocker("đã ghi đánh giá buổi tập", "from dbo.training_evaluations where recorded_by_user_id=?"),
            new Blocker("đã xử lý khóa huấn luyện", "from dbo.training_locks where ? in (locked_by_user_id, released_by_user_id)"),
            new Blocker("đã tham gia công việc chăm sóc", "from dbo.care_tasks where ? in (assigned_staff_id, completed_by_user_id, created_by_user_id)"),
            new Blocker("đã lập hoặc hoàn thành chăm sóc phòng ngừa", "from dbo.preventive_care_schedules where ? in (created_by_user_id, completed_by_user_id)"),
            new Blocker("đã cấu hình ngưỡng thể lực", "from dbo.horse_metric_thresholds where configured_by_user_id=?"),
            new Blocker("đã ghi dữ liệu theo dõi thể lực", "from dbo.telemetry_sessions where recorded_by_user_id=?"),
            new Blocker("đã xác nhận cảnh báo", "from dbo.alert_events where acknowledged_by_user_id=?"),
            new Blocker("đã tạo hoặc được gắn vào bản ghi nghiệp vụ", "from dbo.module_records where user_id=?"),
            new Blocker("đã tạo giải đua", "from dbo.race_events where created_by_user_id=?"),
            new Blocker("đã có giao dịch tài chính", "from dbo.financial_transactions where ? in (wallet_owner_user_id, recorded_by_user_id)"),
            new Blocker("đã có báo cáo chủ ngựa", "from dbo.owner_reports where ? in (owner_user_id, generated_by_user_id)"),
            new Blocker("đã thay đổi ma trận quyền", "from dbo.role_permissions where changed_by_user_id=?"),
            new Blocker("đã thực hiện thao tác nghiệp vụ hoặc quản trị", "from dbo.audit_logs where actor_user_id=? and module_code<>'AUTH' and description not like '%/api/auth/%'")
    );

    private static final List<Blocker> ACTIVE_ASSIGNMENT_BLOCKERS = List.of(
            new Blocker("vẫn đang sở hữu ngựa đang hoạt động", "from dbo.horse_ownerships o join dbo.horses h on h.id=o.horse_id where o.owner_id=? and h.archived_at is null"),
            new Blocker("vẫn phụ trách ngựa đang hoạt động", "from dbo.horses where archived_at is null and ? in (current_trainer_id, caretaker_user_id)"),
            new Blocker("vẫn là bác sĩ phụ trách ngựa đang hoạt động", "from dbo.horses where archived_at is null and diagnosing_vet_user_id=?"),
            new Blocker("vẫn đang giữ ca khám ban đầu", "from dbo.module_records where module_code='MEDICAL' and record_type='MEDICAL_ASSIGNMENT' and status='ACTIVE' and user_id=?"),
            new Blocker("vẫn đang giữ ca tái khám", "from dbo.module_records where module_code='MEDICAL' and record_type='MEDICAL_FOLLOW_UP' and status in ('ASSIGNED','HANDOVER_REQUESTED') and user_id=?"),
            new Blocker("vẫn có công việc chăm sóc chưa hoàn thành", "from dbo.care_tasks where assigned_staff_id=? and status='PENDING'")
    );

    public AccountActivityGuard(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void requireEligibleForRoleChange(UUID userId) {
        rejectFirstMatch(userId, ROLE_CHANGE_BLOCKERS, "Không thể đổi vai trò vì tài khoản ",
                ". Hãy dùng tài khoản mới cho vai trò khác.");
    }

    public void requireNoActiveAssignments(UUID userId) {
        rejectFirstMatch(userId, ACTIVE_ASSIGNMENT_BLOCKERS, "Không thể khóa tài khoản vì người dùng ",
                ". Hãy bàn giao hoặc hoàn tất nhiệm vụ trước.");
    }

    private void rejectFirstMatch(UUID userId, List<Blocker> blockers, String prefix, String suffix) {
        for (Blocker blocker : blockers) {
            Integer count = jdbc.queryForObject(
                    "select case when exists (select 1 " + blocker.sql() + ") then 1 else 0 end",
                    Integer.class, userId);
            if (count != null && count > 0) {
                throw new BusinessException(prefix + blocker.description() + suffix);
            }
        }
    }
}
