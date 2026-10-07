param(
    [string]$SqlServer = "localhost,1433",
    [string]$SqlUsername = "sa",
    [string]$SqlPassword = "sa12345",
    [switch]$RecreateDatabase
)

$ErrorActionPreference = "Stop"

$sqlcmdCommand = Get-Command sqlcmd -ErrorAction SilentlyContinue
if (-not $sqlcmdCommand) {
    throw "sqlcmd was not found. Install SQL Server Command Line Utilities and try again."
}

if ($RecreateDatabase) {
    Write-Host "Dropping existing database racehorse_local if present..." -ForegroundColor Yellow
    $dropSql = "IF DB_ID(N'racehorse_local') IS NOT NULL BEGIN ALTER DATABASE [racehorse_local] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE [racehorse_local]; END;"
    & $sqlcmdCommand.Source -b -S $SqlServer -U $SqlUsername -P $SqlPassword -C -Q $dropSql
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to drop database racehorse_local."
    }
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
    "10_seed_demo_data.sql"
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
    # -f 65001 forces UTF-8 encoding for Vietnamese characters
    & $sqlcmdCommand.Source -b -f 65001 -S $SqlServer -U $SqlUsername -P $SqlPassword -C -i $scriptPath
    if ($LASTEXITCODE -ne 0) {
        throw "Execution failed on script: $scriptName"
    }
}

Write-Host ""
Write-Host "CURRENT BACKEND LOCAL BASELINE applied successfully!" -ForegroundColor Green
Write-Host "Database racehorse_local is ready." -ForegroundColor Green
