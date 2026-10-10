param(
    [string]$SqlServer = "localhost,1433",
    [string]$SqlUsername = "sa",
    [string]$SqlPassword = "",
    [switch]$UseWindowsAuth,
    [switch]$RecreateDatabase,
    [switch]$IncludeSync
)

$ErrorActionPreference = "Stop"

if (-not $UseWindowsAuth -and [string]::IsNullOrWhiteSpace($SqlPassword)) {
    throw "Specify -SqlPassword for SQL Authentication, or use -UseWindowsAuth."
}

$sqlcmdCommand = Get-Command sqlcmd -ErrorAction SilentlyContinue
if (-not $sqlcmdCommand) {
    throw "sqlcmd was not found. Install SQL Server Command Line Utilities and try again."
}

function Run-SqlCmd {
    param(
        [string]$ScriptPath,
        [string]$Query
    )

    if ($UseWindowsAuth) {
        if ($Query) {
            & $sqlcmdCommand.Source -b -S $SqlServer -E -C -Q $Query
        } else {
            & $sqlcmdCommand.Source -b -f 65001 -S $SqlServer -E -C -i $ScriptPath
        }
    } else {
        if ($Query) {
            & $sqlcmdCommand.Source -b -S $SqlServer -U $SqlUsername -P $SqlPassword -C -Q $Query
        } else {
            & $sqlcmdCommand.Source -b -f 65001 -S $SqlServer -U $SqlUsername -P $SqlPassword -C -i $ScriptPath
        }
    }

    if ($LASTEXITCODE -ne 0) {
        if ($Query) {
            throw "Execution failed for query: $Query"
        } else {
            throw "Execution failed for script: $ScriptPath"
        }
    }
}

if ($RecreateDatabase) {
    Write-Host "Dropping existing database racehorse_local if present..." -ForegroundColor Yellow
    $dropSql = "IF DB_ID(N'racehorse_local') IS NOT NULL BEGIN ALTER DATABASE [racehorse_local] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE [racehorse_local]; END;"
    Run-SqlCmd -Query $dropSql
}

$baselineScripts = @(
    "00_create_database.sql",
    "01_core_auth.sql",
    "02_horses_and_profiles.sql",
    "03_facilities.sql",
    "04_training_management.sql",
    "05_health_and_care.sql",
    "06_telemetry_and_alerts.sql",
    "07_racing_and_finance.sql",
    "08_compact_records.sql",
    "09_seed_reference_data.sql",
    "10_seed_demo_data.sql",
    "32_stall_assignment_concurrency_lock.sql",
    "33_race_registration_lifecycle_integrity.sql",
    "34_hourly_racetrack_slots.sql",
    "35_horse_intake_workflow.sql"
)

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "Applying CURRENT BACKEND LOCAL BASELINE to racehorse_local" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

foreach ($scriptName in $baselineScripts) {
    $scriptPath = Join-Path $PSScriptRoot $scriptName
    if (-not (Test-Path -LiteralPath $scriptPath)) {
        throw "Missing required baseline script: $scriptName"
    }

    Write-Host "Executing [$scriptName]..." -ForegroundColor Gray
    Run-SqlCmd -ScriptPath $scriptPath
}

Write-Host ""
Write-Host "CURRENT BACKEND LOCAL BASELINE applied successfully!" -ForegroundColor Green

if ($IncludeSync) {
    Write-Host ""
    Write-Host "==========================================================" -ForegroundColor Cyan
    Write-Host "Applying optional SHARED TEAM DATA from DATABASE/sync..." -ForegroundColor Cyan
    Write-Host "==========================================================" -ForegroundColor Cyan
    $syncDir = Join-Path $PSScriptRoot "sync"
    if (Test-Path -LiteralPath $syncDir) {
        $syncFiles = Get-ChildItem -Path $syncDir -Filter "*.sql" -File | Sort-Object Name
        if ($syncFiles.Count -gt 0) {
            foreach ($sFile in $syncFiles) {
                Write-Host "Executing sync script [$($sFile.Name)]..." -ForegroundColor Yellow
                Run-SqlCmd -ScriptPath $sFile.FullName
            }
            Write-Host "All shared team sync scripts applied successfully." -ForegroundColor Green
        } else {
            Write-Host "No sync scripts found in $syncDir. Skipping." -ForegroundColor Gray
        }
    }
}

Write-Host "Database racehorse_local is ready." -ForegroundColor Green
