[CmdletBinding()]
param(
    [switch]$ValidateOnly
)

$ErrorActionPreference = 'Stop'

$envFile = Join-Path $PSScriptRoot '.env'
if (-not (Test-Path -LiteralPath $envFile -PathType Leaf)) {
    throw 'The project .env file is missing.'
}

$passwordLines = @(
    Get-Content -LiteralPath $envFile |
        Where-Object { $_ -match '^\s*DB_PASSWORD=' }
)
if ($passwordLines.Count -ne 1) {
    throw 'The .env file must contain exactly one DB_PASSWORD assignment.'
}

$passwordLine = $passwordLines[0]
$dbPassword = $passwordLine.Substring($passwordLine.IndexOf('=') + 1)
if ([string]::IsNullOrWhiteSpace($dbPassword)) {
    throw 'DB_PASSWORD is empty in .env.'
}
if ($dbPassword.Trim() -eq 'replace_with_your_actual_postgresql_password') {
    throw 'Replace the DB_PASSWORD placeholder in .env before continuing.'
}

if ($ValidateOnly) {
    Write-Output 'The .env configuration is valid; the password value was not displayed.'
    exit 0
}

$previousPassword = [Environment]::GetEnvironmentVariable('DB_PASSWORD', 'Process')
$hadPreviousPassword = Test-Path Env:DB_PASSWORD
$repositoryPath = Join-Path $env:USERPROFILE '.m2\repository'
$repositoryArgument = "-Dmaven.repo.local=$repositoryPath"
$exitCode = 1

try {
    $env:DB_PASSWORD = $dbPassword
    $mavenWrapper = Join-Path $PSScriptRoot 'mvnw.cmd'
    & $mavenWrapper $repositoryArgument 'spring-boot:run'
    $exitCode = $LASTEXITCODE
}
finally {
    if ($hadPreviousPassword) {
        $env:DB_PASSWORD = $previousPassword
    }
    else {
        Remove-Item Env:DB_PASSWORD -ErrorAction SilentlyContinue
    }
}

exit $exitCode
