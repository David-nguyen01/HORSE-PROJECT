-- PostgreSQL equivalents of the seven SQL Server business triggers.
-- All application objects remain in the private dbo schema.

CREATE OR REPLACE FUNCTION dbo.audit_logs_append_only() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'Nhật ký kiểm toán là dữ liệu chỉ ghi thêm, không được sửa hoặc xóa.';
END;
$$;
CREATE TRIGGER tr_audit_logs_append_only
BEFORE UPDATE OR DELETE ON dbo.audit_logs
FOR EACH ROW EXECUTE FUNCTION dbo.audit_logs_append_only();

CREATE OR REPLACE FUNCTION dbo.enforce_training_window() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE local_start timestamp;
BEGIN
  IF NEW.cancelled_at IS NOT NULL THEN RETURN NEW; END IF;
  IF TG_OP = 'UPDATE' THEN
    IF OLD.cancelled_at IS NULL AND
       NEW.scheduled_start IS NOT DISTINCT FROM OLD.scheduled_start AND
       NEW.scheduled_end IS NOT DISTINCT FROM OLD.scheduled_end THEN
      RETURN NEW;
    END IF;
  END IF;
  local_start := NEW.scheduled_start AT TIME ZONE 'Asia/Ho_Chi_Minh';
  IF EXTRACT(HOUR FROM local_start) NOT BETWEEN 6 AND 23
     OR EXTRACT(MINUTE FROM local_start) <> 0
     OR EXTRACT(SECOND FROM local_start) <> 0
     OR NEW.scheduled_end <> NEW.scheduled_start + INTERVAL '1 hour' THEN
    RAISE EXCEPTION 'Lịch huấn luyện phải là slot 1 giờ bắt đầu từ 06:00 đến 23:00.';
  END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER tr_enforce_training_window
BEFORE INSERT OR UPDATE ON dbo.training_schedules
FOR EACH ROW EXECUTE FUNCTION dbo.enforce_training_window();

CREATE OR REPLACE FUNCTION dbo.race_registration_status_guard() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE race_id_text text;
BEGIN
  IF NEW.module_code <> 'RACING' OR NEW.record_type <> 'RACE_REGISTRATION'
     OR NEW.status IS NOT DISTINCT FROM OLD.status THEN RETURN NEW; END IF;
  IF NEW.status = 'CONFIRMED' AND OLD.status IS DISTINCT FROM 'APPROVE' THEN
    RAISE EXCEPTION 'Chỉ đăng ký đã được chủ ngựa duyệt mới có thể xác nhận.';
  END IF;
  IF NEW.status = 'WITHDRAWN' AND OLD.status = 'CONFIRMED' THEN
    RAISE EXCEPTION 'Không thể rút đăng ký đã được xác nhận.';
  END IF;
  IF NEW.status IN ('WITHDRAWN', 'CONFIRMED') THEN
    race_id_text := NEW.data_json::jsonb ->> 'raceEventId';
    IF race_id_text IS NOT NULL AND EXISTS (
      SELECT 1 FROM dbo.race_events WHERE id = race_id_text::uuid AND completed_at IS NOT NULL
    ) THEN
      RAISE EXCEPTION 'Không thể thay đổi đăng ký sau khi giải đua đã hoàn thành.';
    END IF;
  END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER tr_module_records_race_registration_status_guard
BEFORE UPDATE ON dbo.module_records
FOR EACH ROW EXECUTE FUNCTION dbo.race_registration_status_guard();

-- Unique indexes protect simultaneous writers, unlike a count-only trigger.
CREATE UNIQUE INDEX ux_stall_assignment_active_stall
ON dbo.module_records ((data_json::jsonb ->> 'stallId'))
WHERE module_code='STABLE_CARE' AND record_type='STALL_ASSIGNMENT' AND status='ACTIVE';
CREATE UNIQUE INDEX ux_stall_assignment_active_horse
ON dbo.module_records (horse_id)
WHERE module_code='STABLE_CARE' AND record_type='STALL_ASSIGNMENT' AND status='ACTIVE' AND horse_id IS NOT NULL;

CREATE OR REPLACE FUNCTION dbo.stall_assignment_capacity_guard() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE stable_id_text text;
DECLARE occupied integer;
BEGIN
  IF NEW.module_code <> 'STABLE_CARE' OR NEW.record_type <> 'STALL_ASSIGNMENT'
     OR NEW.status <> 'ACTIVE' THEN RETURN NEW; END IF;
  stable_id_text := NEW.data_json::jsonb ->> 'stableId';
  IF stable_id_text IS NULL THEN RAISE EXCEPTION 'Phân ô chuồng thiếu stableId.'; END IF;
  PERFORM 1 FROM dbo.stables WHERE id = stable_id_text::uuid FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'Không tìm thấy khu chuồng.'; END IF;
  SELECT count(DISTINCT horse_id) INTO occupied FROM dbo.module_records
   WHERE module_code='STABLE_CARE' AND record_type='STALL_ASSIGNMENT'
     AND status='ACTIVE' AND id <> NEW.id
     AND data_json::jsonb ->> 'stableId' = stable_id_text;
  IF occupied >= 4 THEN RAISE EXCEPTION 'Khu chuồng này đã đạt sức chứa tối đa 4 con ngựa.'; END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER tr_module_records_stall_assignment_capacity
BEFORE INSERT OR UPDATE ON dbo.module_records
FOR EACH ROW EXECUTE FUNCTION dbo.stall_assignment_capacity_guard();

CREATE OR REPLACE FUNCTION dbo.training_evaluation_plan_expiry() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE active_plan boolean;
DECLARE last_phase_end date;
BEGIN
  SELECT p.is_active, (SELECT max(ph.end_date) FROM dbo.training_plan_phases ph WHERE ph.training_plan_id=p.id)
    INTO active_plan, last_phase_end
    FROM dbo.training_schedules s
    JOIN dbo.training_plan_phases phase ON phase.id=s.training_plan_phase_id
    JOIN dbo.training_plans p ON p.id=phase.training_plan_id
   WHERE s.id=NEW.training_schedule_id;
  IF active_plan IS FALSE OR last_phase_end < (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Ho_Chi_Minh')::date THEN
    RAISE EXCEPTION 'Kế hoạch đã hết hạn, không thể tạo đánh giá mới.';
  END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER tr_training_evaluations_plan_expiry
BEFORE INSERT ON dbo.training_evaluations
FOR EACH ROW EXECUTE FUNCTION dbo.training_evaluation_plan_expiry();

CREATE OR REPLACE FUNCTION dbo.training_schedule_plan_and_lock() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE plan_horse_id uuid;
DECLARE plan_active boolean;
DECLARE phase_start date;
DECLARE phase_end date;
DECLARE last_phase_end date;
DECLARE horse_status text;
DECLARE horse_health text;
DECLARE schedule_changed boolean;
DECLARE status_started boolean;
DECLARE intensity_changed boolean;
DECLARE reactivated boolean;
BEGIN
  IF TG_OP = 'INSERT' THEN
    schedule_changed := TRUE;
    status_started := NEW.session_status='IN_PROGRESS';
    intensity_changed := TRUE;
    reactivated := FALSE;
  ELSE
    schedule_changed := NEW.training_plan_phase_id IS DISTINCT FROM OLD.training_plan_phase_id
      OR NEW.horse_id IS DISTINCT FROM OLD.horse_id
      OR NEW.scheduled_start IS DISTINCT FROM OLD.scheduled_start
      OR NEW.scheduled_end IS DISTINCT FROM OLD.scheduled_end;
    status_started := NEW.session_status='IN_PROGRESS' AND OLD.session_status IS DISTINCT FROM 'IN_PROGRESS';
    intensity_changed := NEW.workload_intensity IS DISTINCT FROM OLD.workload_intensity;
    reactivated := OLD.cancelled_at IS NOT NULL;
  END IF;
  SELECT p.horse_id, p.is_active, ph.start_date, ph.end_date,
         (SELECT max(ph2.end_date) FROM dbo.training_plan_phases ph2 WHERE ph2.training_plan_id=p.id)
    INTO plan_horse_id, plan_active, phase_start, phase_end, last_phase_end
    FROM dbo.training_plan_phases ph JOIN dbo.training_plans p ON p.id=ph.training_plan_id
   WHERE ph.id=NEW.training_plan_phase_id;
  IF plan_horse_id IS DISTINCT FROM NEW.horse_id THEN
    RAISE EXCEPTION 'Ngựa trên lịch phải trùng với ngựa của kế hoạch huấn luyện.';
  END IF;
  IF NEW.cancelled_at IS NOT NULL THEN RETURN NEW; END IF;
  -- Lock the horse row so two concurrent schedule writers cannot pass the same-day check.
  SELECT status, health_status INTO horse_status, horse_health
    FROM dbo.horses WHERE id=NEW.horse_id FOR UPDATE;
  IF (schedule_changed OR reactivated) AND
     (plan_active IS FALSE OR last_phase_end < (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Ho_Chi_Minh')::date
     OR (NEW.scheduled_start AT TIME ZONE 'Asia/Ho_Chi_Minh')::date NOT BETWEEN phase_start AND phase_end) THEN
    RAISE EXCEPTION 'Ngày tập phải nằm trong giai đoạn huấn luyện còn hiệu lực.';
  END IF;
  IF (schedule_changed OR status_started OR reactivated) AND
     (horse_status='INJURED' OR horse_health='INJURED') THEN
    RAISE EXCEPTION 'Ngựa đang bị chấn thương, không thể xếp hoặc bắt đầu buổi tập.';
  END IF;
  IF NEW.workload_intensity='HIGH' AND (schedule_changed OR status_started OR intensity_changed OR reactivated) AND EXISTS (
    SELECT 1 FROM dbo.training_locks WHERE horse_id=NEW.horse_id AND released_at IS NULL
  ) THEN
    RAISE EXCEPTION 'Không thể xếp lịch cường độ cao khi ngựa đang bị khóa huấn luyện.';
  END IF;
  IF (schedule_changed OR reactivated) AND EXISTS (
    SELECT 1 FROM dbo.training_schedules s
     WHERE s.horse_id=NEW.horse_id AND s.id<>NEW.id AND s.cancelled_at IS NULL
       AND ((s.scheduled_start AT TIME ZONE 'Asia/Ho_Chi_Minh')::date =
            (NEW.scheduled_start AT TIME ZONE 'Asia/Ho_Chi_Minh')::date
         OR (NEW.scheduled_start < s.scheduled_end AND NEW.scheduled_end > s.scheduled_start))
  ) THEN
    RAISE EXCEPTION 'Ngựa đã có buổi tập trong ngày này.';
  END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER tr_training_schedules_plan_and_lock
BEFORE INSERT OR UPDATE ON dbo.training_schedules
FOR EACH ROW EXECUTE FUNCTION dbo.training_schedule_plan_and_lock();

CREATE OR REPLACE FUNCTION dbo.trial_run_booking_guard() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE session_track uuid;
DECLARE session_start timestamptz;
DECLARE session_end timestamptz;
DECLARE session_cancelled timestamptz;
DECLARE lane_track uuid;
DECLARE lane_active boolean;
DECLARE lane_number integer;
BEGIN
  IF NEW.cancelled_at IS NOT NULL THEN RETURN NEW; END IF;
  PERFORM 1 FROM dbo.racetrack_lanes WHERE id=NEW.racetrack_lane_id FOR UPDATE;
  SELECT racetrack_id, is_active, lane_number INTO lane_track, lane_active, lane_number
    FROM dbo.racetrack_lanes WHERE id=NEW.racetrack_lane_id;
  SELECT racetrack_id, scheduled_start, scheduled_end, cancelled_at
    INTO session_track, session_start, session_end, session_cancelled
    FROM dbo.training_schedules WHERE id=NEW.training_schedule_id;
  IF session_track IS DISTINCT FROM lane_track OR session_cancelled IS NOT NULL
     OR lane_active IS DISTINCT FROM TRUE OR lane_number NOT BETWEEN 1 AND 4
     OR NEW.booked_start IS DISTINCT FROM session_start OR NEW.booked_end IS DISTINCT FROM session_end THEN
    RAISE EXCEPTION 'Thông tin làn chạy và thời gian đặt làn phải khớp với buổi tập đang hoạt động.';
  END IF;
  IF EXISTS (
    SELECT 1 FROM dbo.trial_run_bookings b
     WHERE b.racetrack_lane_id=NEW.racetrack_lane_id AND b.id<>NEW.id
       AND b.cancelled_at IS NULL
       AND NEW.booked_start < b.booked_end AND NEW.booked_end > b.booked_start
  ) THEN
    RAISE EXCEPTION 'Làn chạy đã được đặt trong khung giờ này.';
  END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER tr_trial_run_bookings_no_lane_overlap
BEFORE INSERT OR UPDATE ON dbo.trial_run_bookings
FOR EACH ROW EXECUTE FUNCTION dbo.trial_run_booking_guard();
