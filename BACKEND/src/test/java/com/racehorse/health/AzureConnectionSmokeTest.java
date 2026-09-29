package com.racehorse.health;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class AzureConnectionSmokeTest {

    @Test
    void testAzureConnectivity() {
        String url = System.getenv("DB_URL");
        if (url == null || url.isBlank()) {
            url = "jdbc:sqlserver://racehorse-training-management.database.windows.net:1433;databaseName=racehorse;encrypt=true;trustServerCertificate=true";
        }
        String user = System.getenv("DB_USERNAME");
        if (user == null || user.isBlank()) {
            user = "racehorse_admin";
        }
        String password = System.getenv("DB_PASSWORD");

        System.out.println("Azure Target Server: racehorse-training-management.database.windows.net:1433");
        System.out.println("Azure Target Database: racehorse");
        System.out.println("Azure Target User: " + user);
        System.out.println("DB_PASSWORD provided in env: " + (password != null && !password.isBlank()));

        if (password == null || password.isBlank()) {
            System.out.println("AZURE CONNECTION: BLOCKED");
            System.out.println("Reason: DB_PASSWORD environment variable is not set in current environment.");
            return;
        }

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("AZURE CONNECTION: SUCCESS");
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT @@SERVERNAME AS server_name, DB_NAME() AS database_name, @@VERSION AS version")) {
                    if (rs.next()) {
                        System.out.println("Server Name: " + rs.getString("server_name"));
                        System.out.println("Database Name: " + rs.getString("database_name"));
                        System.out.println("Version: " + rs.getString("version"));
                    }
                }

                // Table list
                List<String> tables = new ArrayList<>();
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT TABLE_SCHEMA, TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_TYPE = 'BASE TABLE' ORDER BY TABLE_SCHEMA, TABLE_NAME")) {
                    while (rs.next()) {
                        tables.add(rs.getString("TABLE_SCHEMA") + "." + rs.getString("TABLE_NAME"));
                    }
                }
                System.out.println("Table count: " + tables.size());
                System.out.println("Tables on Azure: " + tables);

                // Row counts for Flow 1, 2, 3 tables
                String[] flowTables = {
                        "horses", "horse_ownerships", "horse_profile_files", "race_results",
                        "training_plans", "training_plan_phases", "training_schedules", "training_evaluations",
                        "telemetry_sessions", "telemetry_readings", "horse_metric_thresholds",
                        "module_records", "training_locks", "preventive_care_schedules",
                        "notifications", "stables", "users"
                };

                System.out.println("--- ROW COUNTS ---");
                for (String t : flowTables) {
                    try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS cnt FROM dbo." + t)) {
                        if (rs.next()) {
                            System.out.println("Row count dbo." + t + ": " + rs.getInt("cnt"));
                        }
                    } catch (Exception e) {
                        System.out.println("Error querying dbo." + t + ": " + e.getMessage());
                    }
                }

                // Check columns for key tables
                String[] inspectTables = {"horses", "training_evaluations", "module_records", "training_locks", "preventive_care_schedules"};
                System.out.println("--- KEY TABLE COLUMNS ---");
                for (String t : inspectTables) {
                    System.out.println("Columns of dbo." + t + ":");
                    try (ResultSet rs = stmt.executeQuery(
                            "SELECT COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE " +
                            "FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = '" + t + "' ORDER BY ORDINAL_POSITION")) {
                        while (rs.next()) {
                            String cName = rs.getString("COLUMN_NAME");
                            String dType = rs.getString("DATA_TYPE");
                            int maxLen = rs.getInt("CHARACTER_MAXIMUM_LENGTH");
                            String nullable = rs.getString("IS_NULLABLE");
                            System.out.println("  - " + cName + " (" + dType + (maxLen > 0 ? "(" + maxLen + ")" : "") + ", nullable: " + nullable + ")");
                        }
                    }
                }

                // Constraints & Checks
                System.out.println("--- CHECK CONSTRAINTS ---");
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT cc.CONSTRAINT_NAME, cc.CHECK_CLAUSE " +
                        "FROM INFORMATION_SCHEMA.CHECK_CONSTRAINTS cc " +
                        "JOIN INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc ON cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME " +
                        "WHERE tc.TABLE_NAME IN ('horses', 'module_records', 'training_evaluations', 'training_locks')")) {
                    while (rs.next()) {
                        System.out.println("  - " + rs.getString("CONSTRAINT_NAME") + ": " + rs.getString("CHECK_CLAUSE"));
                    }
                }

                // Read sample data
                System.out.println("--- SAMPLE READ (horses) ---");
                try (ResultSet rs = stmt.executeQuery("SELECT TOP 3 id, code, name, health_status FROM dbo.horses")) {
                    while (rs.next()) {
                        System.out.println("  Horse: id=" + rs.getString("id") + ", code=" + rs.getString("code") +
                                ", name=" + rs.getString("name") + ", health_status=" + rs.getString("health_status"));
                    }
                }

                System.out.println("--- SAMPLE READ (module_records) ---");
                try (ResultSet rs = stmt.executeQuery("SELECT TOP 3 id, module_code, record_type, status, event_date FROM dbo.module_records")) {
                    while (rs.next()) {
                        System.out.println("  ModuleRecord: id=" + rs.getString("id") + ", module=" + rs.getString("module_code") +
                                ", type=" + rs.getString("record_type") + ", status=" + rs.getString("status"));
                    }
                }
            }

            // Safe Rollback Smoke Test
            System.out.println("--- TRANSACTION ROLLBACK SMOKE TEST ---");
            conn.setAutoCommit(false);
            try (Statement txStmt = conn.createStatement()) {
                String testId = java.util.UUID.randomUUID().toString();
                int rows = txStmt.executeUpdate(
                        "INSERT INTO dbo.module_records (id, module_code, record_type, status, data_json, created_at, updated_at) " +
                        "VALUES ('" + testId + "', 'SYSTEM', 'CONNECTIVITY_TEST', 'TEST', '{\"test\":true}', SYSDATETIMEOFFSET(), SYSDATETIMEOFFSET())");
                System.out.println("Rollback Test: Inserted rows = " + rows);

                // Verify it can be read within the transaction
                try (ResultSet rs = txStmt.executeQuery("SELECT id FROM dbo.module_records WHERE id = '" + testId + "'")) {
                    if (rs.next()) {
                        System.out.println("Rollback Test: Successfully read uncommitted insert within tx.");
                    }
                }

                conn.rollback();
                System.out.println("Rollback Test: Transaction rolled back successfully.");
            } catch (Exception ex) {
                conn.rollback();
                System.out.println("Rollback Test Failed: " + ex.getMessage());
            } finally {
                conn.setAutoCommit(true);
            }

            // Verify test record is NOT in DB after rollback
            try (Statement verifyStmt = conn.createStatement()) {
                try (ResultSet rs = verifyStmt.executeQuery("SELECT COUNT(*) AS cnt FROM dbo.module_records WHERE record_type = 'CONNECTIVITY_TEST'")) {
                    if (rs.next()) {
                        System.out.println("Post-rollback count of test records: " + rs.getInt("cnt") + " (Expected 0)");
                    }
                }
            }

        } catch (SQLException e) {
            System.out.println("AZURE CONNECTION: FAILED");
            System.out.println("ErrorCode: " + e.getErrorCode());
            System.out.println("SQLState: " + e.getSQLState());
            System.out.println("Message: " + e.getMessage());
        }
    }
}
