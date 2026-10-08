param(
    [ValidateSet('real', 'fake')]
    [string]$Gateway = 'fake'
)

$ErrorActionPreference = 'Stop'

$localDbPassword = [Environment]::GetEnvironmentVariable('RACEHORSE_LOCAL_DB_PASSWORD', 'User')
if ([string]::IsNullOrWhiteSpace($localDbPassword)) {
    $localDbPassword = [Environment]::GetEnvironmentVariable('RACEHORSE_LOCAL_DB_PASSWORD', 'Process')
}
if ([string]::IsNullOrWhiteSpace($localDbPassword)) {
    throw 'RACEHORSE_LOCAL_DB_PASSWORD is missing from the Windows user and current process environments.'
}

$env:RACEHORSE_LOCAL_DB_PASSWORD = $localDbPassword
$env:SPRING_PROFILES_ACTIVE = 'local'
$env:PAYOS_GATEWAY = $Gateway
$env:SPRING_JPA_SHOW_SQL = 'false'
if ($Gateway -eq 'real') {
    foreach ($name in @('PAYOS_CLIENT_ID', 'PAYOS_API_KEY', 'PAYOS_CHECKSUM_KEY')) {
        $value = [Environment]::GetEnvironmentVariable($name, 'User')
        if ([string]::IsNullOrWhiteSpace($value)) {
            $value = [Environment]::GetEnvironmentVariable($name, 'Process')
        }
        if ([string]::IsNullOrWhiteSpace($value)) {
            throw "$name is missing from the Windows user and current process environments."
        }
        [Environment]::SetEnvironmentVariable($name, $value, 'Process')
    }
    if ([string]::IsNullOrWhiteSpace($env:PAYOS_RETURN_URL)) {
        $env:PAYOS_RETURN_URL = 'http://localhost:5173/wallet'
    }
    if ([string]::IsNullOrWhiteSpace($env:PAYOS_CANCEL_URL)) {
        $env:PAYOS_CANCEL_URL = 'http://localhost:5173/wallet'
    }
}
$env:JAVA_HOME = (Resolve-Path (Join-Path $PSScriptRoot '..\.tools\jdk-21.0.12.1+1')).Path
$env:Path = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:Path
$maven = (Resolve-Path (Join-Path $PSScriptRoot '..\.tools\apache-maven-3.9.16\bin\mvn.cmd')).Path

Push-Location $PSScriptRoot
try {
    & $maven -s maven-settings.xml spring-boot:run
    if ($LASTEXITCODE -ne 0) {
        throw "Backend exited with code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}
