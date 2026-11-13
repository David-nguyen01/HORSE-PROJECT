package com.racehorse.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

@Configuration
public class DatabaseCompatibilityConfiguration {

    @Bean
    @Primary
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new CompatibleJdbcTemplate(dataSource);
    }

    public static class CompatibleJdbcTemplate extends JdbcTemplate {
        private static final Set<String> DBO_TABLES = Set.of(
            "alert_events", "audit_logs", "care_routine_definitions", "care_tasks", "daily_feeding_logs",
            "financial_transactions", "horse_metric_thresholds", "horse_ownerships",
            "horse_profile_files", "horses", "login_sessions", "module_records",
            "notifications", "owner_reports", "preventive_care_schedules", "race_events",
            "race_results", "racetrack_lanes", "racetracks", "role_permissions",
            "service_packages", "stables", "stored_files", "telemetry_readings", "telemetry_sessions",
            "training_evaluations", "training_locks", "training_plan_phases",
            "training_plans", "training_schedules", "trial_run_bookings", "user_roles", "users"
        );

        private static final java.util.regex.Pattern TABLE_REF_PATTERN = java.util.regex.Pattern.compile(
            "(?i)\\b(from|into|update|join)\\s+([a-zA-Z0-9_]+)"
        );

        public CompatibleJdbcTemplate(DataSource dataSource) {
            super(dataSource);
        }

        public static String cleanSql(String sql) {
            if (sql == null) return null;
            // The application is PostgreSQL-only. This wrapper only resolves legacy
            // unqualified table names into the project's dbo schema.
            Matcher matcher = TABLE_REF_PATTERN.matcher(sql);
            StringBuilder sb = new StringBuilder();
            while (matcher.find()) {
                String keyword = matcher.group(1);
                String tableName = matcher.group(2);
                if (DBO_TABLES.contains(tableName.toLowerCase())) {
                    matcher.appendReplacement(sb, keyword + " dbo." + tableName);
                } else {
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
                }
            }
            matcher.appendTail(sb);
            return sb.toString();
        }

        @Override
        public <T> T query(String sql, ResultSetExtractor<T> rse) {
            return super.query(cleanSql(sql), rse);
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper) {
            return super.query(cleanSql(sql), rowMapper);
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            return super.query(cleanSql(sql), rowMapper, args);
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType) {
            return super.queryForObject(cleanSql(sql), requiredType);
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            return super.queryForObject(cleanSql(sql), requiredType, args);
        }

        @Override
        public <T> T queryForObject(String sql, RowMapper<T> rowMapper, Object... args) {
            return super.queryForObject(cleanSql(sql), rowMapper, args);
        }

        @Override
        public Map<String, Object> queryForMap(String sql, Object... args) {
            return super.queryForMap(cleanSql(sql), args);
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            return super.queryForList(cleanSql(sql), args);
        }

        @Override
        public <T> List<T> queryForList(String sql, Class<T> elementType, Object... args) {
            return super.queryForList(cleanSql(sql), elementType, args);
        }

        @Override
        public int update(String sql, Object... args) {
            return super.update(cleanSql(sql), args);
        }

        @Override
        public int update(String sql) {
            return super.update(cleanSql(sql));
        }

        @Override
        public void execute(String sql) {
            super.execute(cleanSql(sql));
        }
    }
}

