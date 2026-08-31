param(
    [Parameter(Mandatory = $true)][string]$BaseUrl
)

$ErrorActionPreference = "Stop"
$base = $BaseUrl.TrimEnd("/")

$health = Invoke-RestMethod -Uri "$base/api/health"
if ($health.status -ne "UP") { throw "Health check did not report UP." }

$providers = Invoke-RestMethod -Uri "$base/api/llm/providers"
if ($null -eq $providers.providers -or $providers.providers.Count -lt 1) {
    throw "No public LLM provider is available."
}

$page = Invoke-WebRequest -Uri "$base/"
if ($page.StatusCode -ne 200 -or $page.Content -notmatch '<div id="app"></div>') {
    throw "Production frontend is not being served."
}

$openApi = Invoke-WebRequest -Uri "$base/openapi.yaml"
if ($openApi.StatusCode -ne 200 -or $openApi.Content -notmatch 'openapi:') {
    throw "OpenAPI document is unavailable."
}

Write-Host "Smoke tests passed for $base"
