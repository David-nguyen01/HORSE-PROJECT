param(
    [string]$SqlServer = "localhost,1433",
    [string]$SqlUsername = "sa",
    [string]$SqlPassword = "sa123456"
)

$sqlcmdCommand = Get-Command sqlcmd -ErrorAction SilentlyContinue
if (-not $sqlcmdCommand) {
    throw "sqlcmd was not found. Install SQL Server Command Line Utilities and try again."
}

$fullDatabasePath = Join-Path $PSScriptRoot "racehorse_database_full.sql"
if (Test-Path -LiteralPath $fullDatabasePath) {
    $migrationFiles = @(Get-Item -LiteralPath $fullDatabasePath)
} else {
    $migrationFiles = Get-ChildItem -LiteralPath $PSScriptRoot -Filter "[0-9][0-9]_*.sql" -File | Sort-Object Name
}
if ($migrationFiles.Count -eq 0) {
    throw "No SQL migration files were found in $PSScriptRoot."
}

foreach ($migrationFile in $migrationFiles) {
    Write-Host "Running $($migrationFile.Name)..."
    & $sqlcmdCommand.Source -b -S $SqlServer -U $SqlUsername -P $SqlPassword -C -i $migrationFile.FullName
    if ($LASTEXITCODE -ne 0) {
        throw "Migration failed: $($migrationFile.Name)"
    }
}

Write-Host "Database racehorse is ready." -ForegroundColor Green
