[CmdletBinding()]
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $ViteArguments
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$frontendDirectory = Join-Path $projectRoot "frontend"
$nodeModulesDirectory = Join-Path $frontendDirectory "node_modules"
$npmCommand = Get-Command npm.cmd -ErrorAction SilentlyContinue

if ($null -eq $npmCommand) {
    throw "npm was not found. Install Node.js 20 or newer and try again."
}

Push-Location $frontendDirectory
try {
    if (-not (Test-Path -LiteralPath $nodeModulesDirectory)) {
        Write-Host "Installing frontend dependencies..."
        & $npmCommand.Source install
        if ($LASTEXITCODE -ne 0) {
            throw "Unable to install frontend dependencies."
        }
    }

    Write-Host "Starting the frontend at http://localhost:5173 ..."
    & $npmCommand.Source run dev -- @ViteArguments
    if ($LASTEXITCODE -ne 0) {
        throw "The frontend development server stopped with exit code $LASTEXITCODE."
    }
} finally {
    Pop-Location
}
