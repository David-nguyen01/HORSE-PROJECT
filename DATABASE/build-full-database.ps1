param(
    [string]$OutputPath = (Join-Path $PSScriptRoot "racehorse_database_full.sql"),
    [string]$DatabaseName = "racehorse"
)

if ($DatabaseName -notmatch '^[A-Za-z][A-Za-z0-9_]{0,127}$') {
    throw "DatabaseName must contain only letters, numbers and underscores, and must begin with a letter."
}

$migrationNames = @(
    "00_create_database.sql",
    "01_create_schema.sql",
    "02_seed_data.sql",
    "03_horse_role_features.sql",
    "04_cloudinary_profile_files.sql",
    "05_training_evaluations.sql",
    "06_training_plans_scheduling_monitoring.sql",
    "07_health_medical.sql",
    "08_stable_care_inventory.sql",
    "09_racing_reporting_rbac_audit.sql",
    "10_confirmed_business_rules.sql",
    "11_confirmed_automation_and_governance.sql",
    "12_compact_schema.sql",
    "15_training_operational_window.sql",
    "16_seed_training_racetracks.sql",
    "17_align_training_operational_schema.sql",
    "18_training_plan_rest_days_and_evaluation_metrics.sql",
    "19_training_lanes_expiry_and_metrics.sql",
    "20_evaluation_exertion_nullable_and_session_notes.sql"
)

$header = @"
/*
 * Racehorse Training Management System
 * Complete Microsoft SQL Server database installation
 *
 * Covers 20 business modules derived from FR01-FR23:
 *  01 Horse profile, ownership, files and weight
 *  02 Pedigree and race history
 *  03 Fitness dashboard and training evaluation (FR03 + FR07)
 *  04 Phased training plans
 *  05 Daily schedules and trial-run bookings
 *  06 Realtime telemetry and alerts
 *  07 Health map and medical examinations (FR08 + FR09)
 *  08 3D injury markers and recovery
 *  09 Emergency training locks
 *  10 Preventive care
 *  11 Stable allocation and daily routines
 *  12 Nutrition plans
 *  13 Daily care tasks
 *  14 Stable incident reports
 *  15 Inventory and replenishment
 *  16 Race events and registrations
 *  17 Owner and club reporting (FR19 + FR22)
 *  18 Master data administration
 *  19 Fixed five-role RBAC
 *  20 Append-only audit log
 *
 * Shared infrastructure: users, login sessions, notifications and stored files.
 * This script is rerunnable. Hibernate remains ddl-auto=none.
 */

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;
GO
"@

$sections = New-Object System.Collections.Generic.List[string]
$sections.Add($header.Trim())

foreach ($migrationName in $migrationNames) {
    $migrationPath = Join-Path $PSScriptRoot $migrationName
    if (-not (Test-Path -LiteralPath $migrationPath)) {
        throw "Missing migration: $migrationName"
    }

    $content = [System.IO.File]::ReadAllText($migrationPath)
    if ($DatabaseName -ne "racehorse") {
        $content = $content.Replace("[racehorse]", "[$DatabaseName]")
        $content = $content.Replace("N'racehorse'", "N'$DatabaseName'")
    }

    if ($migrationName -eq "09_racing_reporting_rbac_audit.sql") {
        $duplicateAssignmentPattern = "(?s)IF OBJECT_ID\(N'dbo\.user_role_assignments', N'U'\) IS NULL.*?\r?\nGO\r?\n\r?\nDECLARE @roles"
        $content = [System.Text.RegularExpressions.Regex]::Replace(
            $content,
            $duplicateAssignmentPattern,
            "/* user_role_assignments intentionally omitted; user_roles is the canonical account-role table. */`r`nDECLARE @roles"
        )

        $roleUniqueConstraint = "CONSTRAINT uq_role_definitions_code UNIQUE (code)"
        $fixedRoleConstraint = @"
CONSTRAINT uq_role_definitions_code UNIQUE (code),
        CONSTRAINT ck_role_definitions_fixed_code CHECK (code IN (
            'CLUB_MANAGER', 'HEAD_TRAINER', 'STABLE_STAFF', 'HORSE_OWNER', 'VETERINARIAN'))
"@
        $content = $content.Replace($roleUniqueConstraint, $fixedRoleConstraint.Trim())

        $roleConstraintUpgrade = @"
IF NOT EXISTS (
    SELECT 1 FROM sys.check_constraints
    WHERE name = N'ck_role_definitions_fixed_code'
      AND parent_object_id = OBJECT_ID(N'dbo.role_definitions'))
BEGIN
    ALTER TABLE dbo.role_definitions ADD CONSTRAINT ck_role_definitions_fixed_code CHECK (code IN (
        'CLUB_MANAGER', 'HEAD_TRAINER', 'STABLE_STAFF', 'HORSE_OWNER', 'VETERINARIAN'));
END;
GO
"@
        $roleToModulePattern = "(?s)(IF OBJECT_ID\(N'dbo\.role_definitions'.*?\r?\nEND;\r?\nGO)(\r?\n\r?\nIF OBJECT_ID\(N'dbo\.module_permissions')"
        $roleToModuleRegex = New-Object System.Text.RegularExpressions.Regex($roleToModulePattern)
        $content = $roleToModuleRegex.Replace(
            $content,
            { param($match) $match.Groups[1].Value + "`r`n`r`n" + $roleConstraintUpgrade.Trim() + $match.Groups[2].Value },
            1
        )
    }

    $sectionHeader = "/* ========================================================================== */`r`n/* Source: $migrationName */`r`n/* ========================================================================== */"
    $sections.Add($sectionHeader + "`r`n" + $content.Trim())
}

$fullScript = ($sections -join "`r`n`r`n") + "`r`n"
$utf8WithBom = New-Object System.Text.UTF8Encoding($true)
[System.IO.File]::WriteAllText($OutputPath, $fullScript, $utf8WithBom)

Write-Host "Generated $OutputPath" -ForegroundColor Green
