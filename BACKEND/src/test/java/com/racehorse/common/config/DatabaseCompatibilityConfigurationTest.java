package com.racehorse.common.config;

import org.junit.jupiter.api.Test;

import static com.racehorse.common.config.DatabaseCompatibilityConfiguration.CompatibleJdbcTemplate.cleanSql;
import static org.assertj.core.api.Assertions.assertThat;

class DatabaseCompatibilityConfigurationTest {

    @Test
    void postgresUsesNativeTimestampAndBooleanLiterals() {
        String sql = cleanSql(
                "update users set updated_at=SYSDATETIMEOFFSET(), enabled=1 where id=?",
                true);

        assertThat(sql).isEqualTo(
                "update dbo.users set updated_at=CURRENT_TIMESTAMP, enabled=true where id=?");
    }

    @Test
    void sqlServerKeepsItsTimestampAndBitLiterals() {
        String sql = cleanSql(
                "update users set updated_at=SYSDATETIMEOFFSET(), enabled=1 where id=?",
                false);

        assertThat(sql).isEqualTo(
                "update dbo.users set updated_at=SYSDATETIMEOFFSET(), enabled=1 where id=?");
    }

    @Test
    void postgresConvertsGeneratedIdsAndActiveFilters() {
        String sql = cleanSql(
                "insert into notifications(id) select NEWID() from users where enabled=1 and service_override=0",
                true);

        assertThat(sql).isEqualTo(
                "insert into dbo.notifications(id) select gen_random_uuid() from dbo.users where enabled=true and service_override=false");
    }

    @Test
    void postgresLimitsNestedTopOneAtItsOwnSelectBoundary() {
        String sql = cleanSql(
                "select h.id, (select top 1 reason from training_locks where horse_id=h.id order by locked_at desc) as reason from horses h",
                true);

        assertThat(sql).isEqualTo(
                "select h.id, (select reason from dbo.training_locks where horse_id=h.id order by locked_at desc LIMIT 1) as reason from dbo.horses h");
    }

    @Test
    void postgresConvertsUuidAndTimestampFormatting() {
        String sql = cleanSql(
                "update module_records set data_json=JSON_MODIFY(data_json,'$.at',CONVERT(varchar(33),SYSDATETIMEOFFSET(),127)), user_id=CONVERT(varchar(36),?)",
                true);

        assertThat(sql).contains("to_char(CURRENT_TIMESTAMP AT TIME ZONE 'UTC'");
        assertThat(sql).contains("cast(? as varchar(36))");
    }

    @Test
    void postgresPreservesSimpleRowLockAndSqlServerKeepsItsHint() {
        String original = "select id from horses with (UPDLOCK,HOLDLOCK) where id=?";

        assertThat(cleanSql(original, true))
                .isEqualTo("select id from dbo.horses where id=? FOR UPDATE");
        assertThat(cleanSql(original, false))
                .isEqualTo("select id from dbo.horses with (UPDLOCK,HOLDLOCK) where id=?");
    }

    @Test
    void postgresRewritesJsonValueAndPrefixesServicePackages() {
        String query = "select id from service_packages where JSON_VALUE(data_json, '$.packageId') = ?";

        assertThat(cleanSql(query, true))
                .isEqualTo("select id from dbo.service_packages where dbo.json_value(data_json, '$.packageId') = ?");
        assertThat(cleanSql(query, false))
                .isEqualTo("select id from dbo.service_packages where JSON_VALUE(data_json, '$.packageId') = ?");
    }

    @Test
    void broadcastDismissalQueryCompatibleOnBothDialects() {
        String dismissalQuery = "select count(*) from module_records where module_code='SYSTEM' "
                + "and record_type='PACKAGE_DISCONTINUATION_DISMISSAL' and user_id=? "
                + "and lower(JSON_VALUE(data_json, '$.notificationId')) = lower(?)";

        String pgSql = cleanSql(dismissalQuery, true);
        assertThat(pgSql).contains("from dbo.module_records");
        assertThat(pgSql).contains("lower(dbo.json_value(data_json, '$.notificationId')) = lower(?)");

        String sqlServerSql = cleanSql(dismissalQuery, false);
        assertThat(sqlServerSql).contains("from dbo.module_records");
        assertThat(sqlServerSql).contains("lower(JSON_VALUE(data_json, '$.notificationId')) = lower(?)");
    }
}
