package com.racehorse.training.service;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

/** Deletes a session and its dependent records inside the caller's transaction. */
final class TrainingSessionDeletion {
    private TrainingSessionDeletion() { }

    static void delete(JdbcTemplate jdbc, UUID scheduleId) {
        if (tableExists(jdbc, "training_evaluation_files"))
            jdbc.update("delete from training_evaluation_files where training_evaluation_id in "
                    + "(select id from training_evaluations where training_schedule_id=?)", scheduleId);
        jdbc.update("delete from training_evaluations where training_schedule_id=?", scheduleId);
        jdbc.update("delete from alert_events where telemetry_session_id in "
                + "(select id from telemetry_sessions where training_schedule_id=?)", scheduleId);
        jdbc.update("delete from telemetry_readings where telemetry_session_id in "
                + "(select id from telemetry_sessions where training_schedule_id=?)", scheduleId);
        jdbc.update("delete from telemetry_sessions where training_schedule_id=?", scheduleId);
        if (tableExists(jdbc, "daily_tasks")) {
            if (tableExists(jdbc, "daily_task_files"))
                jdbc.update("delete from daily_task_files where daily_task_id in "
                        + "(select id from daily_tasks where training_schedule_id=?)", scheduleId);
            jdbc.update("delete from daily_tasks where training_schedule_id=?", scheduleId);
        }
        jdbc.update("delete from trial_run_bookings where training_schedule_id=?", scheduleId);
        jdbc.update("delete from training_schedules where id=?", scheduleId);
    }

    private static boolean tableExists(JdbcTemplate jdbc, String name) {
        Integer count = jdbc.queryForObject("select count(*) from sys.tables where schema_id=SCHEMA_ID('dbo') and name=?",
                Integer.class, name);
        return count != null && count > 0;
    }
}
