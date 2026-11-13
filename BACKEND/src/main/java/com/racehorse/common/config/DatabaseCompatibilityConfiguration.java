package com.racehorse.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Configuration
public class DatabaseCompatibilityConfiguration {

    @Bean
    @Primary
    public JdbcTemplate jdbcTemplate(
            DataSource dataSource,
            @Value("${spring.datasource.driver-class-name:}") String driverClassName) {
        return new CompatibleJdbcTemplate(dataSource, "org.postgresql.Driver".equals(driverClassName));
    }

    public static class CompatibleJdbcTemplate extends JdbcTemplate {
        private final boolean postgresql;

        private static final Set<String> DBO_TABLES = Set.of(
            "alert_events", "audit_logs", "care_routine_definitions", "care_tasks",
            "financial_transactions", "horse_metric_thresholds", "horse_ownerships",
            "horse_profile_files", "horses", "login_sessions", "module_records",
            "notifications", "owner_reports", "preventive_care_schedules", "race_events",
            "race_results", "racetrack_lanes", "racetracks", "role_permissions",
            "service_packages", "stables", "stored_files", "telemetry_readings", "telemetry_sessions",
            "training_evaluations", "training_locks", "training_plan_phases",
            "training_plans", "training_schedules", "trial_run_bookings", "user_roles", "users"
        );

        private static final Pattern TABLE_REF_PATTERN = Pattern.compile(
            "(?i)\\b(from|into|update|join)\\s+([a-zA-Z0-9_]+)"
        );
        private static final Pattern TOP_ONE_PATTERN = Pattern.compile("(?i)\\bselect\\s+top\\s+1\\b");

        public CompatibleJdbcTemplate(DataSource dataSource, boolean postgresql) {
            super(dataSource);
            this.postgresql = postgresql;
        }

        public static String cleanSql(String sql, boolean postgresql) {
            if (sql == null) return null;
            boolean lockRequested = postgresql
                    && Pattern.compile("(?i)\\bwith\\s*\\([^)]*\\bupdlock\\b[^)]*\\)").matcher(sql).find();
            if (postgresql) {
                sql = sql.replaceAll("(?i)\\bSYSDATETIMEOFFSET\\s*\\(\\s*\\)", "CURRENT_TIMESTAMP");
                sql = sql.replaceAll("(?i)\\bNEWID\\s*\\(\\s*\\)", "gen_random_uuid()");
                sql = rewriteTopOne(sql);
                sql = sql.replaceAll("(?i)\\bDATEADD\\s*\\(\\s*day\\s*,", "dbo.dateadd('day',");
                sql = sql.replaceAll(
                        "(?i)\\bCONVERT\\s*\\(\\s*varchar\\s*\\(\\s*(\\d+)\\s*\\)\\s*,\\s*\\?\\s*\\)",
                        "cast(? as varchar($1))");
                sql = sql.replaceAll(
                        "(?i)\\bCONVERT\\s*\\(\\s*varchar\\s*\\(\\s*33\\s*\\)\\s*,\\s*CURRENT_TIMESTAMP\\s*,\\s*127\\s*\\)",
                        "to_char(CURRENT_TIMESTAMP AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS.MS\"Z\"')");
                sql = sql.replace("SE Asia Standard Time", "Asia/Ho_Chi_Minh");
                sql = sql.replaceAll(
                        "(?i)\\bCONVERT\\s*\\(\\s*date\\s*,\\s*CURRENT_TIMESTAMP\\s+AT\\s+TIME\\s+ZONE\\s+'Asia/Ho_Chi_Minh'\\s*\\)",
                        "(CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Ho_Chi_Minh')::date");
                sql = sql.replaceAll(
                        "(?i)\\bCONVERT\\s*\\(\\s*varchar\\s*\\(\\s*10\\s*\\)\\s*,\\s*([a-z_][a-z0-9_.]*)\\s*,\\s*23\\s*\\)",
                        "to_char($1, 'YYYY-MM-DD')");
                sql = sql.replaceAll(
                        "(?i)\\bCONVERT\\s*\\(\\s*varchar\\s*\\(\\s*10\\s*\\)\\s*,\\s*\\?\\s*,\\s*23\\s*\\)",
                        "to_char(cast(? as date), 'YYYY-MM-DD')");
                sql = sql.replaceAll(
                        "(?i)\\b(is_active|is_primary_contact|service_override|granted|injured|enabled|closes_next_day)\\s*=\\s*1\\b",
                        "$1=true");
                sql = sql.replaceAll(
                        "(?i)\\b(is_active|is_primary_contact|service_override|granted|injured|enabled|closes_next_day)\\s*=\\s*0\\b",
                        "$1=false");
                sql = sql.replaceAll("(?i)\\b(?<!dbo\\.)JSON_VALUE\\s*\\(", "dbo.json_value(");
                sql = sql.replaceAll("(?i)\\b(?<!dbo\\.)JSON_MODIFY\\s*\\(", "dbo.json_modify(");
                // PostgreSQL uses row locks after SELECT, not SQL Server table hints.
                sql = sql.replaceAll("(?i)\\s+with\\s*\\([a-zA-Z0-9_,\\s]*(?:updlock|holdlock)[a-zA-Z0-9_,\\s]*\\)", "");
            }
            // PostgreSQL requires parentheses around parameters in OFFSET/FETCH: OFFSET (?) ROWS FETCH NEXT (?) ROWS ONLY
            sql = sql.replaceAll("(?i)\\boffset\\s*\\(?\\s*\\?\\s*\\)?\\s*rows\\s+fetch\\s+next\\s*\\(?\\s*\\?\\s*\\)?\\s*rows\\s+only", "OFFSET (?) ROWS FETCH NEXT (?) ROWS ONLY");
            // Automatically prefix known tables with dbo. if no schema is specified
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
            String result = sb.toString();
            if (lockRequested && result.matches("(?is)^\\s*select\\s+(?:id\\b|\\*).*")) {
                result = result.stripTrailing() + " FOR UPDATE";
            }
            return result;
        }

        private static String rewriteTopOne(String sql) {
            Matcher matcher = TOP_ONE_PATTERN.matcher(sql);
            while (matcher.find()) {
                int depth = 0;
                boolean quoted = false;
                for (int i = 0; i < matcher.start(); i++) {
                    char ch = sql.charAt(i);
                    if (ch == '\'') {
                        if (quoted && i + 1 < sql.length() && sql.charAt(i + 1) == '\'') i++;
                        else quoted = !quoted;
                    } else if (!quoted) {
                        if (ch == '(') depth++;
                        else if (ch == ')') depth--;
                    }
                }
                int selectDepth = depth;
                int boundary = sql.length();
                for (int i = matcher.end(); i < sql.length(); i++) {
                    char ch = sql.charAt(i);
                    if (ch == '\'') {
                        if (quoted && i + 1 < sql.length() && sql.charAt(i + 1) == '\'') i++;
                        else quoted = !quoted;
                    } else if (!quoted) {
                        if (ch == '(') depth++;
                        else if (ch == ')') {
                            if (depth == selectDepth) {
                                boundary = i;
                                break;
                            }
                            depth--;
                        }
                    }
                }
                sql = sql.substring(0, matcher.start()) + "select"
                        + sql.substring(matcher.end(), boundary) + " LIMIT 1"
                        + sql.substring(boundary);
                matcher = TOP_ONE_PATTERN.matcher(sql);
            }
            return sql;
        }

        @Override
        public <T> T query(String sql, ResultSetExtractor<T> rse) {
            return super.query(cleanSql(sql, postgresql), rse);
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper) {
            return super.query(cleanSql(sql, postgresql), rowMapper);
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            return super.query(cleanSql(sql, postgresql), rowMapper, args);
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType) {
            return super.queryForObject(cleanSql(sql, postgresql), requiredType);
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            return super.queryForObject(cleanSql(sql, postgresql), requiredType, args);
        }

        @Override
        public <T> T queryForObject(String sql, RowMapper<T> rowMapper, Object... args) {
            return super.queryForObject(cleanSql(sql, postgresql), rowMapper, args);
        }

        @Override
        public Map<String, Object> queryForMap(String sql, Object... args) {
            return super.queryForMap(cleanSql(sql, postgresql), args);
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            return super.queryForList(cleanSql(sql, postgresql), args);
        }

        @Override
        public <T> List<T> queryForList(String sql, Class<T> elementType, Object... args) {
            return super.queryForList(cleanSql(sql, postgresql), elementType, args);
        }

        @Override
        public int update(String sql, Object... args) {
            return super.update(cleanSql(sql, postgresql), args);
        }

        @Override
        public int update(String sql) {
            return super.update(cleanSql(sql, postgresql));
        }

        @Override
        public void execute(String sql) {
            super.execute(cleanSql(sql, postgresql));
        }
    }
}

