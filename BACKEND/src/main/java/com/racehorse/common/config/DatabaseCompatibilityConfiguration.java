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

@Configuration
public class DatabaseCompatibilityConfiguration {

    @Bean
    @Primary
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new CompatibleJdbcTemplate(dataSource);
    }

    public static class CompatibleJdbcTemplate extends JdbcTemplate {

        public CompatibleJdbcTemplate(DataSource dataSource) {
            super(dataSource);
        }

        public static String cleanSql(String sql) {
            if (sql == null) return null;
            // Strip SQL Server table hints like WITH (UPDLOCK,HOLDLOCK) or WITH (UPDLOCK) or WITH (HOLDLOCK)
            return sql.replaceAll("(?i)\\s+with\\s*\\([a-zA-Z0-9_,\\s]*(?:updlock|holdlock)[a-zA-Z0-9_,\\s]*\\)", "");
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
