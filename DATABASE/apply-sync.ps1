param(
    [string]$SqlServer = "localhost,1433",
    [string]$SqlUsername = "sa",
    [string]$SqlPassword = "sa12345",
    [string]$Script = ""
)

$ErrorActionPreference = "Stop"

$sqlcmdCommand = Get-Command sqlcmd -ErrorAction SilentlyContinue
if (-not $sqlcmdCommand) {
    throw "sqlcmd was not found. Install SQL Server Command Line Utilities and try again."
}

$syncDir = Join-Path $PSScriptRoot "sync"
if (-not (Test-Path -LiteralPath $syncDir)) {
    throw "Directory not found: $syncDir"
}

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "Applying SHARED TEAM DATA SYNC to racehorse_local" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

if ($Script -and $Script.Trim() -ne "") {
    # Apply single specified script
    $targetPath = $Script
    if (-not (Test-Path -LiteralPath $targetPath)) {
        $targetPath = Join-Path $syncDir $Script
    }
    if (-not (Test-Path -LiteralPath $targetPath)) {
        throw "Specified sync script was not found: $Script (checked path: $targetPath)"
    }

    $scriptName = Split-Path $targetPath -Leaf
    Write-Host "Executing sync script [$scriptName]..." -ForegroundColor Yellow
    & $sqlcmdCommand.Source -b -f 65001 -S $SqlServer -U $SqlUsername -P $SqlPassword -C -i $targetPath
    if ($LASTEXITCODE -ne 0) {
        throw "Execution failed on sync script: $scriptName"
    }
    Write-Host "Sync script [$scriptName] applied successfully." -ForegroundColor Green
} else {
    # Apply all SQL scripts in DATABASE/sync/ in alphabetical order
    $sqlFiles = Get-ChildItem -Path $syncDir -Filter "*.sql" -File | Sort-Object Name
    if ($sqlFiles.Count -eq 0) {
        Write-Host "No sync scripts (*.sql) found in $syncDir." -ForegroundColor Gray
        Write-Host "Database racehorse_local remains at canonical baseline." -ForegroundColor Gray
        exit 0
    }

    Write-Host "Found $($sqlFiles.Count) sync script(s) in $syncDir." -ForegroundColor Gray
    foreach ($file in $sqlFiles) {
        Write-Host "Executing sync script [$($file.Name)]..." -ForegroundColor Yellow
        & $sqlcmdCommand.Source -b -f 65001 -S $SqlServer -U $SqlUsername -P $SqlPassword -C -i $file.FullName
        if ($LASTEXITCODE -ne 0) {
            throw "Execution failed on sync script: $($file.Name)"
        }
    }
    Write-Host ""
    Write-Host "All shared team sync scripts applied successfully!" -ForegroundColor Green
}
