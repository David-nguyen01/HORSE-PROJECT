# Database Governance and Architecture Guide

**Project:** Racehorse Training Management System  
**Target Environment:** Local Microsoft SQL Server (`localhost:1433`)  
**Database Name:** `racehorse_local`  
**Current Baseline Status:** `CURRENT BACKEND LOCAL BASELINE` (Verified as of 2026-10-07)

---

## 1. Database Purpose

This directory (`DATABASE/`) contains the authoritative, modular SQL scripts and governance policies for managing the local Microsoft SQL Server database of the Racehorse Training Management System.

### 1.1 Local Runtime & Connection
- **Engine:** Microsoft SQL Server 2019/2022 (or Developer / Express Edition).
- **Default Port:** `localhost:1433`.
- **Database Name:** `racehorse_local`.
- **Collation:** `Vietnamese_100_CI_AS_SC` (supports full Vietnamese Unicode search, case-insensitive, accent-sensitive, supplementary characters).
- **Recovery Model:** `SIMPLE` (prevents uncontrolled log file expansion during local development).
- **Default Credentials:** User `sa` / Password `sa12345` (configurable via environment variables `DB_USERNAME` and `DB_PASSWORD`).

### 1.2 Relationship Between Backend and Database
The Spring Boot backend is strictly configured with:
```properties
spring.jpa.hibernate.ddl-auto=none
```
Hibernate will **never** generate, alter, drop, or migrate tables automatically. The application relies entirely on the SQL scripts in this directory as an independent underlying foundation.

### 1.3 Source-of-Truth Rule
- **The CURRENT BACKEND code is the primary source of truth for database schema expectations.** The database must satisfy the JPA entities, JdbcTemplate queries, and service contracts in the currently checked-out backend code.
- **Azure SQL is completely out of scope.** Do not consult or sync with Azure SQL.
- **TV2 (Veterinarian) feature code is not yet integrated into MAIN.** Do not invent tables, columns, or triggers for future TV2 features until that code is integrated into the backend.

---

## 2. Database Structure

The active files in `DATABASE/` are organized into a clean, numbered modular sequence:

```
DATABASE/
├── 00_create_database.sql          # Creates racehorse_local DB with Vietnamese collation & SIMPLE recovery
├── 01_core_auth.sql                # Auth & RBAC tables (users, user_roles, login_sessions, role_permissions)
├── 02_horses_and_profiles.sql      # Horse registry, ownerships, weight, and Cloudinary stored files
├── 03_facilities.sql               # Stables, racetracks (05:00-11:00 window), and racetrack_lanes (4 lanes/track)
├── 04_training_management.sql      # Training plans, phases, schedules, trial runs, evaluations, locks & 5 triggers
├── 05_health_and_care.sql          # Care routine definitions, daily care tasks (Flow 4), preventive care schedules
├── 06_telemetry_and_alerts.sql     # Biometric thresholds, telemetry sessions, sensor readings, alert events
├── 07_racing_and_finance.sql       # Races, race results, financial transactions (VND default), reports, audit logs & 1 trigger
├── 08_compact_records.sql          # Compact JSON document store (module_records) for stalls, inventory, nutrition
├── 09_seed_reference_data.sql      # Essential system seed: 70 RBAC permissions, 5 role accounts, facilities, care routines
├── 10_seed_demo_data.sql           # Representative demo seed: 3 horses, stall assignments, sample training plan & VND cash flows
├── sync/                           # Controlled directory for shared team runtime scenario scripts
│   └── .gitkeep                    # Preserves directory in Git when no active sync scripts are pending
├── apply-sync.ps1                  # Applies shared team sync scripts to existing local database without rebuild
├── setup-local.ps1                 # Automated PowerShell setup script (runs 00..10, optional -IncludeSync)
├── README.md                       # This permanent database governance document
└── TRAINING_V3_IMPACT.md           # Architecture impact and verification record for Training V3
```

### 2.1 File Categorization & Roles
1. **Schema Scripts (`00_` through `08_`):**
   Define the physical DDL (tables, primary keys, foreign keys, check constraints, default constraints, indexes, and triggers).
2. **Reference / System Seed Data (`09_seed_reference_data.sql`):**
   Contains permanent system reference records: the complete RBAC permission matrix (70 keys), default seed accounts for all 5 roles, 10 racetracks with 4 lanes each (40 lanes), 2 default stables, and standard care routines.
3. **Demo Data (`10_seed_demo_data.sql`):**
   Provides realistic sample entities (3 horses: H01, H02, H03; stall allocations; nutrition assignments; training plan; financial ledger records in VND) so that developers and testers can verify features immediately without manual entry.
4. **Shared Team Sync Data (`sync/*.sql`):**
   Contains reproducible SQL scripts capturing specific shared runtime data scenarios created during development/testing that another teammate must reproduce.
5. **Setup & Sync Scripts (`setup-local.ps1`, `apply-sync.ps1`):**
   PowerShell automation scripts that execute baseline and sync scripts sequentially with UTF-8 codepage (`65001`).
6. **Documentation (`README.md`, `TRAINING_V3_IMPACT.md`):**
   Governance standards, operational instructions, and architectural impact history.

---

## 3. Database Governance Rules

1. **Backend Contract Primacy:**  
   The CURRENT BACKEND database expectations (JPA annotations, JdbcTemplate SQL, native queries) form the binding schema contract.
2. **Never Weaken Backend Queries:**  
   If a backend query fails due to a missing column or table (e.g. `where currency='VND'`), **never** delete or loosen the query in Java to bypass the database error. Fix the database schema instead.
3. **Canonical File Edits Only:**  
   Do not create duplicate or competing patch scripts (e.g., `01_patch.sql`, `fix_schema.sql`, `v2_schema.sql`). All schema additions must be incorporated directly into the corresponding canonical file (`01_` to `08_`).
4. **No Full Dumps in Git:**  
   Never commit `.bak` files, `.mdf`/`.ldf` binaries, or full database dumps. Version control stores only modular, reviewable `.sql` scripts.
5. **No Personal or Debug Data in Git:**  
   Test scripts, developer scratch data, and ad-hoc debug entries must never be committed to repository seed or sync files.
6. **No Unwarranted Speculative Additions:**  
   Do not add tables or columns based on future SRS features or roles (such as TV2 / Veterinarian) if the current backend code does not actively reference them.
7. **Append-Only Audit Integrity:**  
   The `audit_logs` table is protected by trigger `tr_audit_logs_append_only`. Never bypass or drop this trigger.

---

## 4. Schema File Rules

1. **Execution Order by Dependency:**  
   File numbering (`00_` to `10_`) represents execution order based on foreign key and object dependencies:
   - `00_` Engine & Database -> `01_` Users & Auth -> `02_` Horses -> `03_` Facilities -> `04_` Training -> `05_` Care -> `06_` Telemetry -> `07_` Racing & Finance -> `08_` Compact Records -> `09_` Reference Seed -> `10_` Demo Seed.
2. **Cohesive Object Grouping:**  
   Keep related tables, foreign keys, indexes, check constraints, and table-specific triggers together in the appropriate module script.
3. **Idempotent DDL Design:**  
   Every table creation must be guarded by `IF OBJECT_ID('dbo.table_name', 'U') IS NULL`. Triggers must use `CREATE OR ALTER TRIGGER`.
4. **Collation & Unicode:**  
   All text columns holding Vietnamese content must use `NVARCHAR` (not `VARCHAR`) to preserve diacritics.

---

## 5. Data Rules

To maintain a clean and reliable codebase, data is categorized into 4 distinct tiers:

| Tier | Description | Location | Tracked in Git? |
| :--- | :--- | :--- | :---: |
| **REFERENCE DATA** | Essential system seed: roles, permissions, tracks, lanes, care routine definitions. System cannot start or function without this. | `09_seed_reference_data.sql` | **YES** |
| **DEMO DATA** | Representative baseline test records: sample horses, demo training plans, financial transactions for dashboard testing. | `10_seed_demo_data.sql` | **YES** |
| **SHARED TEAM DATA** | Runtime scenario data created during development/testing that another teammate must reproduce (e.g. cross-role testing). | `DATABASE/sync/` | **YES** |
| **LOCAL TEST DATA** | Ad-hoc entities created during manual testing, UI exploratory testing, or local experimentation. | Local DB only | **STRICTLY NO** |

---

## 6. Shared Team Data Sync Workflow

### 6.1 Why Sync Exists
- Each team member runs their own independent local SQL Server instance (`racehorse_local`).
- Git synchronizes code, canonical schema, reference seed, and baseline demo seed.
- **Git does NOT automatically synchronize runtime data created on one developer's machine.**
- In cross-role workflows (for example: `STABLE_STAFF` creates an inventory replenishment request $\rightarrow$ `CLUB_MANAGER` must review and approve it), teammates need a controlled, deterministic way to reproduce that specific scenario state on their local databases without dumping or resetting the database.

> **Crucial Rule:** Git only synchronizes the SQL sync scripts, NOT a live running database. A teammate must apply the relevant sync script to their own local database.

### 6.2 What Belongs in `DATABASE/sync/`
- Only reproducible runtime data scenarios required for cross-member or cross-role testing.
- Example: An inventory replenishment request created by `STABLE_STAFF` needing approval by `CLUB_MANAGER`.
- Example: A specific scheduled training session created by `HEAD_TRAINER` needing daily care assignment by `STABLE_STAFF`.

### 6.3 What Does NOT Belong in `DATABASE/sync/`
- **Schema DDL:** Table/column changes belong in `01_` through `08_`.
- **System master data:** Fixed catalog items belong in `09_seed_reference_data.sql`.
- **Baseline demo data:** General demo records belong in `10_seed_demo_data.sql`.
- **Personal debug rows:** Ad-hoc temporary test records must stay local and never be committed.
- **Full database dumps:** Raw SSMS table exports or full database dumps are prohibited.

### 6.4 Naming Convention
Sync scripts must follow the strict timestamped naming convention:
```
DATABASE/sync/YYYYMMDD_<short_description>.sql
```
- **Example:** `DATABASE/sync/20261007_staff_inventory_request.sql`
- **If multiple scripts on the same date:** Use unique descriptive names, e.g. `20261007_staff_inventory_request.sql` and `20261007_trainer_evaluation_session.sql`.
- **Prohibited:** Never use `v2`, `v3`, `final`, `fix`, `temp`, or random filenames.

### 6.5 Idempotency and Script Design
Every sync script must be:
1. **Deterministic and safe to run multiple times:** Protected by `IF NOT EXISTS` guards.
2. **Independent from SQL Server `IDENTITY` auto-increments:** Never hard-code identity numbers.
3. **Keyed on stable natural identifiers or deterministic UUIDs:**
   - Look up users by `email` (e.g. `staff@gmail.com`).
   - Look up horses by `code` (e.g. `H01`).
   - Look up facilities by `code` or `name` (e.g. `STABLE-A`, `Sân A`).
   - For `module_records` or transactional tables, use a deterministic UUID or natural composite key guard.

#### Standard Sync Script Template:
```sql
-- Purpose: Reproduce STABLE_STAFF replenishment request for CLUB_MANAGER review
-- Scenario: Cross-role inventory approval workflow
-- Target Database: racehorse_local

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

USE [racehorse_local];
GO

DECLARE @reqId UNIQUEIDENTIFIER = 'D1010000-0000-0000-0000-000000000001';
DECLARE @staffId UNIQUEIDENTIFIER;
DECLARE @staffName NVARCHAR(100);

-- Resolve foreign keys by stable natural keys
SELECT @staffId = id, @staffName = username FROM dbo.users WHERE email = 'staff@gmail.com';

IF @staffId IS NOT NULL
BEGIN
    IF NOT EXISTS (SELECT 1 FROM dbo.module_records WHERE id = @reqId)
    BEGIN
        INSERT INTO dbo.module_records (id, module_code, record_type, source_table, horse_id, user_id, status, event_date, data_json, created_at, updated_at)
        VALUES (
            @reqId, 'INVENTORY', 'REPLENISHMENT_REQUEST', 'api', NULL, @staffId, 'PENDING_APPROVAL',
            CAST(SYSDATETIMEOFFSET() AS DATE),
            N'{"itemId":null,"itemName":"Cỏ Alfalfa Bổ Sung","category":"FEED","requestedQuantity":50,"unit":"kg","reason":"Hết hàng đột xuất","proposedByUserId":"' + CAST(@staffId AS NVARCHAR(36)) + N'","proposedByName":"' + @staffName + N'","proposedAt":"2026-10-07T10:00:00Z","reviewedByUserId":null,"reviewedByName":null,"reviewedAt":null,"reviewNotes":null}',
            SYSDATETIMEOFFSET(), SYSDATETIMEOFFSET()
        );
        PRINT 'Inserted replenishment request ' + CAST(@reqId AS VARCHAR(36));
    END
    ELSE
    BEGIN
        PRINT 'Replenishment request ' + CAST(@reqId AS VARCHAR(36)) + ' already exists. Skipping.';
    END
END;
GO

-- Verification query
SELECT id, module_code, record_type, status, JSON_VALUE(data_json, '$.itemName') AS itemName
FROM dbo.module_records
WHERE id = 'D1010000-0000-0000-0000-000000000001';
GO
```

### 6.6 Multi-Step Approval and State Transitions
In multi-step workflows:
$$\text{STABLE\_STAFF (Creates Request)} \longrightarrow \text{CLUB\_MANAGER (Reviews \& Approves)}$$
- The creation state (`PENDING_APPROVAL`) and approved state (`APPROVED`) are different states.
- **Do NOT commit a sync script for every intermediate click.** Only commit states that teammates must reproduce for testing.
- If a subsequent state is required for a teammate's test scenario, create a subsequent sync script (e.g. `20261007_manager_inventory_approval.sql`) using `UPDATE` statements guarded by natural keys.

### 6.7 Team Sync Protocol

#### Developer A (Producer):
1. Creates the scenario data locally through the UI/API.
2. Verifies the scenario behaves as expected.
3. Inspects the affected database records.
4. Creates a clean, idempotent sync script in `DATABASE/sync/YYYYMMDD_<description>.sql`.
5. Verifies the script locally using `apply-sync.ps1` (verifying idempotency).
6. Reviews the SQL script diff.
7. Commits and pushes the script to Git.

#### Developer B (Consumer):
1. Pulls the latest changes from Git.
2. Applies the sync script to their running local database:
   ```powershell
   powershell -ExecutionPolicy Bypass -File .\DATABASE\apply-sync.ps1
   ```
   *(Or specify `-Script "YYYYMMDD_<description>.sql"` to apply only that script).*
3. Verifies the resulting records.
4. Proceeds with testing their dependent role/feature.

---

## 7. Team Workflow

### 7.1 Before Writing Code
1. Read this `DATABASE/README.md`.
2. Inspect the current schema in `DATABASE/` and verify your local SQL Server instance is running.
3. Inspect the backend JPA entities and repositories for the area you are touching.

### 7.2 Before Pushing Changes
1. If you modified the schema, update the canonical schema script (`01_` through `08_`).
2. If you added system-wide reference data, update `09_seed_reference_data.sql`.
3. If you created a shared cross-role scenario, create a script under `DATABASE/sync/`.
4. Verify the database recreates cleanly from scratch:
   ```powershell
   powershell -ExecutionPolicy Bypass -File .\DATABASE\setup-local.ps1 -RecreateDatabase
   ```
5. Run the backend test suite:
   ```powershell
   mvn test
   ```
6. Ensure no local scratch data or personal test accounts are committed.

### 7.3 After Pulling Teammate Changes
1. Review the git diff in `DATABASE/`.
2. If only sync scripts were added, apply them without rebuilding:
   ```powershell
   powershell -ExecutionPolicy Bypass -File .\DATABASE\apply-sync.ps1
   ```
3. If canonical schema files (`01_` - `08_`) changed, rebuild the baseline:
   ```powershell
   powershell -ExecutionPolicy Bypass -File .\DATABASE\setup-local.ps1 -RecreateDatabase
   ```
4. Re-run `mvn test` to confirm your local environment is aligned.

---

## 8. Rules for Coding Agents

Autonomous and pair-programming coding agents **MUST**:
- Read this `DATABASE/README.md` before making any database modifications.
- Inspect the CURRENT BACKEND code (entities, repositories, controllers, native SQL) before proposing schema changes.
- Verify actual SQL Server metadata via `sqlcmd` or `INFORMATION_SCHEMA` when investigating issues.
- Maintain full reproducibility via `setup-local.ps1` and `apply-sync.ps1`.
- Ensure all sync scripts in `DATABASE/sync/` are strictly idempotent.
- Clean up any synthetic test records created during mechanism verification.
- Update this README whenever database governance, architecture, or workflow changes.
- Explicitly report all created, modified, or removed database files in task summaries.

Coding agents **MUST NOT**:
- Use Azure SQL or historical uncommitted assumptions as a source of truth.
- Invent tables, columns, or constraints not required by current backend code.
- Delete, weaken, or bypass backend SQL query clauses (such as `currency = 'VND'`) to mask a missing database column.
- Create duplicate scripts (e.g. `05_patch.sql`, `21_temp.sql`).
- Dump binary databases or full SQL dumps into Git.
- Commit personal debug or test records.
- Drop or reset the database unnecessarily when a targeted, non-destructive schema fix is sufficient.

---

## 9. Database Setup & Sync Execution Commands

### 9.1 Baseline Setup (`setup-local.ps1`)
```powershell
# 1. Clean recreation from scratch (canonical baseline 00..10 only):
powershell -ExecutionPolicy Bypass -File .\DATABASE\setup-local.ps1 -RecreateDatabase

# 2. Clean recreation + automatically apply all shared sync scripts in sync/:
powershell -ExecutionPolicy Bypass -File .\DATABASE\setup-local.ps1 -RecreateDatabase -IncludeSync

# 3. Apply baseline scripts without dropping the database:
powershell -ExecutionPolicy Bypass -File .\DATABASE\setup-local.ps1
```

### 9.2 Applying Shared Sync Data (`apply-sync.ps1`)
Use this when you pull new sync scripts from Git and want to update your running database without rebuilding:

```powershell
# Apply all pending sync scripts in DATABASE/sync/ in alphabetical order:
powershell -ExecutionPolicy Bypass -File .\DATABASE\apply-sync.ps1

# Apply a single specific sync script:
powershell -ExecutionPolicy Bypass -File .\DATABASE\apply-sync.ps1 -Script "20261007_staff_inventory_request.sql"
```

---

## 10. Current Baseline Specifications

### 10.1 Core Metrics
- **Database Name:** `racehorse_local`
- **Total Tables:** `32` application tables
- **Total Triggers:** `6` active operational triggers
- **Supported Roles:** `CLUB_MANAGER`, `HEAD_TRAINER`, `STABLE_STAFF`, `HORSE_OWNER`, `VETERINARIAN`

### 10.2 Deterministic Test Accounts
All 5 accounts share the default BCrypt password for: **`Admin123`**

| Role | Email | Username | Description |
| :--- | :--- | :--- | :--- |
| `CLUB_MANAGER` | `admin@gmail.com` | `admin` | Full club oversight, financial dashboard, RBAC matrix |
| `HEAD_TRAINER` | `trainer@gmail.com` | `head_trainer` | Training plans, scheduling, sessions, evaluations |
| `STABLE_STAFF` | `staff@gmail.com` | `stable_staff` | Flow 4 daily care tasks, feeding, stall maintenance |
| `HORSE_OWNER` | `owner@gmail.com` | `horse_owner` | Owns demo horses (H01-H03), views training metrics & costs |
| `VETERINARIAN` | `vet@gmail.com` | `vet_dr` | Health checks, preventive schedules, training session oversight |

### 10.3 Major Modules & Table Inventory
1. **Core Auth & RBAC (4):** `users`, `user_roles`, `login_sessions`, `role_permissions`
2. **Horses & Media (5):** `horses`, `horse_ownerships`, `stored_files`, `horse_profile_files`, `horse_weight_records`
3. **Facilities (3):** `stables`, `racetracks`, `racetrack_lanes`
4. **Training Management (6):** `training_plans`, `training_plan_phases`, `training_schedules`, `trial_run_bookings`, `training_evaluations`, `training_locks`
5. **Health & Care (3):** `care_routine_definitions`, `care_tasks`, `preventive_care_schedules`
6. **Telemetry & Alerts (4):** `horse_metric_thresholds`, `telemetry_sessions`, `telemetry_readings`, `alert_events`
7. **Racing, Finance & Audit (6):** `race_events`, `race_results`, `financial_transactions`, `owner_reports`, `notifications`, `audit_logs`
8. **Compact JSON Storage (1):** `module_records`

### 10.4 Operational Triggers
1. `tr_enforce_training_window`: Enforces training schedule sessions within 05:00 - 11:00.
2. `tr_training_schedules_no_staff_overlap`: Prevents staff double-booking for the same time slot.
3. `tr_training_schedules_no_vet_overlap`: Prevents veterinarian double-booking for the same time slot.
4. `tr_trial_run_bookings_no_lane_overlap`: Prevents multiple horses from booking the same racetrack lane on the same day.
5. `tr_training_schedules_plan_and_lock`: Enforces valid plan phase boundaries and blocks training when an active medical or administrative lock is present.
6. `tr_audit_logs_append_only`: Blocks any `UPDATE` or `DELETE` statement against `audit_logs`.

### 10.5 Key Schema Rules
- **Currency:** `financial_transactions.currency` is `CHAR(3) NOT NULL DEFAULT 'VND'`. Backend financial dashboards query with `where currency='VND'`.
- **Photo Storage:** `care_tasks.photo_bytes` is `VARBINARY(MAX)` with explicit conversion support for image uploads.
- **Compact Module Records:** The `module_records` table holds flexible JSON structures (`module_type` + `record_id` + `data` NVARCHAR(MAX)):
  - `STALL` and `STALL_ASSIGNMENT` (Stall management)
  - `INVENTORY_ITEM` and `REPLENISHMENT_REQUEST` (Barn inventory & supply tracking)
  - `STANDARD_FEEDING_PLAN` and `NUTRITION_PLAN` (Feeding schedules & rations)

---

## 11. Baseline Verification Snapshot

> **Notice:** This section records the verified verification state as of **2026-10-07**. Future commits and migrations may update or alter these test figures.

- **Baseline Status:** `CURRENT BACKEND LOCAL BASELINE`
- **Maven Test Suite (`mvn test`):**
  - Tests run: `153`
  - Failures: `0`
  - Errors: `0`
  - Skipped: `4`
- **Spring Boot Backend Startup:**
  - Tomcat started on port `8080` in `14.601s`.
  - JPA / Hibernate initialized with `ddl-auto=none`.
- **API Smoke Test Results:**
  - `POST /api/auth/login`: HTTP 200 OK for all 5 roles.
  - `GET /api/admin/dashboard?month=2026-10`: HTTP 200 OK. Correctly calculates VND figures (`revenue = 50,000,000`, `expense = 28,500,000`, `net = 21,500,000`, `activeHorses = 3`, `activeAccounts = 5`).
  - `GET /api/admin/role-permissions`: HTTP 200 OK (70 permission definitions).
  - `GET /api/horses`: HTTP 200 OK (3 demo horses).
  - `GET /api/care-tasks`: HTTP 200 OK (today's care tasks for STABLE_STAFF).
  - `GET /api/horses/mine`: HTTP 200 OK (3 horses returned for HORSE_OWNER).
  - `GET /api/inventory/replenishments`: HTTP 200 OK.

---

## 12. Troubleshooting Guide

When investigating discrepancies, always follow the core diagnostic comparison:
$$\text{CURRENT BACKEND CONTRACT} \longleftrightarrow \text{ACTUAL LOCAL DATABASE} \longleftrightarrow \text{DATABASE SCRIPT}$$

### 12.1 Backend Reports "Invalid column name '<col>'"
- **Cause:** The database was created using an outdated schema or a column was omitted.
- **Action:** Inspect the JPA entity or native query in Java. Verify whether the column is defined in the corresponding `DATABASE/0x_*.sql` script. If missing from the script, add it with proper types and re-run `setup-local.ps1`. **Do not remove the column from the Java query.**

### 12.2 Backend Reports "Invalid object name '<table_name>'"
- **Cause:** The target table does not exist in `racehorse_local`.
- **Action:** Check `INFORMATION_SCHEMA.TABLES` via `sqlcmd`. Confirm that the corresponding schema file was executed and did not fail on an earlier statement.

### 12.3 Schema Differs Between Teammates
- **Cause:** One teammate made manual ad-hoc changes in SSMS without committing them to the SQL scripts.
- **Action:** Never rely on manual database edits. Run `setup-local.ps1 -RecreateDatabase` from the latest Git commit to bring both machines to the identical baseline.

### 12.4 Authentication Fails ("Bad credentials")
- **Cause:** Seed data was not applied, or user table was truncated.
- **Action:** Verify `09_seed_reference_data.sql` was executed. Check that `users` contains the 5 accounts with password hash `$2a$10$Sf0Drsuesp0Aw0q9W1FFY.8JkfFUgq7U7a991JY80e2zXzEBliH/u` (corresponds to `Admin123`).

### 12.5 Vietnamese Characters Display as "??"
- **Cause:** Scripts were executed without UTF-8 codepage or non-Unicode columns were used.
- **Action:** Ensure text columns use `NVARCHAR` and string literals use `N'...'`. Always run scripts using `setup-local.ps1` or `apply-sync.ps1`, which force `-f 65001`.

### 12.6 SQL Server Connection Failure ("Connection refused" / "Port 1433")
- **Cause:** SQL Server instance is stopped or TCP/IP protocol is disabled in SQL Server Configuration Manager.
- **Action:** Open SQL Server Configuration Manager -> SQL Server Network Configuration -> Protocols -> Enable TCP/IP -> Ensure Port 1433 is active -> Restart SQL Server service.

---

## 13. Maintenance Policy

1. **Canonical File Updates:**  
   When adding a new column or table, edit the corresponding canonical script (`01_` through `08_`).
2. **Deterministic Seed Additions:**  
   When new system permissions or static master records are introduced, add them to `09_seed_reference_data.sql`.
3. **Shared Scenario Syncing:**  
   When creating cross-role test scenarios, commit an idempotent script under `DATABASE/sync/YYYYMMDD_<description>.sql`.
4. **No Competing Script Accumulation:**  
   Do not keep old versions, rollback scripts, or temporary fix files (e.g. `patch1.sql`, `v2.sql`) in `DATABASE/`. Once verified and integrated into the baseline, redundant files must be retired.
5. **Governance Documentation Updates:**  
   Whenever the database structure, triggers, or team workflow change, this `README.md` must be updated immediately to reflect the new verified state.
