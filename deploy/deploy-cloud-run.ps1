param(
    [Parameter(Mandatory = $true)][string]$ProjectId,
    [string]$Region = "europe-west1",
    [string]$Service = "ai-demo",
    [ValidateSet("OPENAI", "GROQ", "GEMINI")][string]$Provider = "OPENAI",
    [Parameter(Mandatory = $true)][string]$ApiKeySecret,
    [string]$EnvironmentFile = "$PSScriptRoot/cloudrun.env.yaml"
)

$ErrorActionPreference = "Stop"
if (-not (Test-Path -LiteralPath $EnvironmentFile)) {
    throw "Environment file not found: $EnvironmentFile. Copy cloudrun.env.yaml.example first."
}

$image = "$Region-docker.pkg.dev/$ProjectId/ai-demo/ai-demo:latest"
$secretVariable = switch ($Provider) {
    "OPENAI" { "OPENAI_API_KEY" }
    "GROQ" { "GROQ_API_KEY" }
    "GEMINI" { "GEMINI_API_KEY" }
}

gcloud config set project $ProjectId
gcloud services enable run.googleapis.com cloudbuild.googleapis.com artifactregistry.googleapis.com firestore.googleapis.com secretmanager.googleapis.com

$repository = gcloud artifacts repositories describe ai-demo --location $Region --format "value(name)" 2>$null
if (-not $repository) {
    gcloud artifacts repositories create ai-demo --repository-format docker --location $Region
}

gcloud builds submit --tag $image .
$temporaryEnvironment = New-TemporaryFile
try {
    $configuredEnvironment = [System.IO.File]::ReadAllText((Resolve-Path -LiteralPath $EnvironmentFile))
    $runtimeEnvironment = "$configuredEnvironment`nLLM_PROVIDER: $Provider`nGOOGLE_CLOUD_PROJECT: $ProjectId`n"
    [System.IO.File]::WriteAllText($temporaryEnvironment.FullName, $runtimeEnvironment)

    gcloud run deploy $Service `
        --image $image `
        --region $Region `
        --allow-unauthenticated `
        --port 8080 `
        --cpu 1 `
        --memory 512Mi `
        --concurrency 5 `
        --min 0 `
        --max 1 `
        --timeout 300 `
        --env-vars-file $temporaryEnvironment.FullName `
        --set-secrets "$secretVariable=$ApiKeySecret`:latest,DEMO_IP_HASH_SALT=demo-ip-hash-salt:latest"
} finally {
    Remove-Item -LiteralPath $temporaryEnvironment.FullName -Force -ErrorAction SilentlyContinue
}

$url = gcloud run services describe $Service --region $Region --format "value(status.url)"
& "$PSScriptRoot/smoke-test.ps1" -BaseUrl $url
Write-Host "Deployment verified at $url"
