-- Keep the existing booking rules while avoiding PL/pgSQL variable/column ambiguity.
CREATE OR REPLACE FUNCTION dbo.trial_run_booking_guard() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  v_session_track uuid;
  v_session_start timestamptz;
  v_session_end timestamptz;
  v_session_cancelled timestamptz;
  v_lane_track uuid;
  v_lane_active boolean;
  v_lane_number integer;
BEGIN
  IF NEW.cancelled_at IS NOT NULL THEN
    RETURN NEW;
  END IF;

  SELECT lane.racetrack_id, lane.is_active, lane.lane_number
    INTO v_lane_track, v_lane_active, v_lane_number
    FROM dbo.racetrack_lanes AS lane
    WHERE lane.id = NEW.racetrack_lane_id
    FOR UPDATE;

  SELECT schedule.racetrack_id, schedule.scheduled_start,
         schedule.scheduled_end, schedule.cancelled_at
    INTO v_session_track, v_session_start, v_session_end, v_session_cancelled
    FROM dbo.training_schedules AS schedule
    WHERE schedule.id = NEW.training_schedule_id;

  IF v_session_track IS DISTINCT FROM v_lane_track
     OR v_session_cancelled IS NOT NULL
     OR v_lane_active IS DISTINCT FROM TRUE
     OR v_lane_number NOT BETWEEN 1 AND 4
     OR NEW.booked_start IS DISTINCT FROM v_session_start
     OR NEW.booked_end IS DISTINCT FROM v_session_end THEN
    RAISE EXCEPTION 'Thông tin làn chạy và thời gian đặt làn phải khớp với buổi tập đang hoạt động.';
  END IF;

  IF EXISTS (
    SELECT 1 FROM dbo.trial_run_bookings AS booking
    WHERE booking.racetrack_lane_id = NEW.racetrack_lane_id
      AND booking.id <> NEW.id
      AND booking.cancelled_at IS NULL
      AND NEW.booked_start < booking.booked_end
      AND NEW.booked_end > booking.booked_start
  ) THEN
    RAISE EXCEPTION 'Làn chạy đã được đặt trong khung giờ này.';
  END IF;

  RETURN NEW;
END;
$$;

ALTER FUNCTION dbo.trial_run_booking_guard()
  SET search_path = pg_catalog, dbo;
