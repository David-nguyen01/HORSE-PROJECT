package com.racehorse.common.config;

import org.junit.jupiter.api.Test;

import static com.racehorse.common.config.DatabaseCompatibilityConfiguration.CompatibleJdbcTemplate.cleanSql;
import static org.assertj.core.api.Assertions.assertThat;

class DatabaseCompatibilityConfigurationTest {

    @Test
    void prefixesKnownTablesWithApplicationSchema() {
        String sql = cleanSql(
                "select u.id from users u join user_roles r on r.user_id=u.id where u.enabled=true");

        assertThat(sql).isEqualTo(
                "select u.id from dbo.users u join dbo.user_roles r on r.user_id=u.id where u.enabled=true");
    }

    @Test
    void keepsNativePostgresPaginationAndRowLocks() {
        String sql = cleanSql(
                "select id from horses where id=? order by id limit ? offset ? for update");

        assertThat(sql).isEqualTo(
                "select id from dbo.horses where id=? order by id limit ? offset ? for update");
    }

    @Test
    void keepsPostgresJsonAndTimestampExpressions() {
        String sql = cleanSql(
                "update module_records set updated_at=current_timestamp, "
                        + "data_json=dbo.json_modify(data_json,'$.status',?) where id=?");

        assertThat(sql).isEqualTo(
                "update dbo.module_records set updated_at=current_timestamp, "
                        + "data_json=dbo.json_modify(data_json,'$.status',?) where id=?");
    }

    @Test
    void leavesUnknownRelationsUntouched() {
        assertThat(cleanSql("select id from custom_view"))
                .isEqualTo("select id from custom_view");
    }
}
