param(
    [Parameter(Mandatory = $true)][string]$ProjectId,
    [string]$Region = "europe-west1",
    [string]$Service = "ai-demo",
    # Runtime identity of the service, created on the first deployment.
    [string]$ServiceAccountName = "ai-demo",
    [ValidateSet("OPENAI", "GROQ", "GEMINI")][string]$Provider = "OPENAI",
    [Parameter(Mandatory = $true)][string]$ApiKeySecret,
    [ValidateSet("", "OPENAI", "GEMINI")][string]$EmbeddingProvider = "",
    [string]$EmbeddingApiKeySecret,
    # Further provider keys to mount as ENV_VAR=secret-name, so the service can offer more than one
    # provider, e.g. GEMINI_API_KEY=gemini-api-key,GROQ_API_KEY=groq-api-key.
    [string[]]$AdditionalSecrets = @(),
    [switch]$DisableRag,
    # Cloud Run's front end is one proxy; use 2 behind an external Application Load Balancer.
    [ValidateRange(0, 10)][int]$TrustedProxyHops = 1,
    [string]$EnvironmentFile = "$PSScriptRoot/cloudrun.env.yaml"
)

$ErrorActionPreference = "Stop"
if (-not (Test-Path -LiteralPath $EnvironmentFile)) {
    throw "Environment file not found: $EnvironmentFile. Copy cloudrun.env.yaml.example first."
}
$configuredEnvironment = [System.IO.File]::ReadAllText((Resolve-Path -LiteralPath $EnvironmentFile))
# Without the hop count the server ignores X-Forwarded-For and every client shares one hourly
# limit, so a copy of the environment file made before the setting existed must still get it.
$proxyHopsEnvironment = ""
if ($configuredEnvironment -match '(?m)^\s*DEMO_LIMITS_TRUSTED_PROXY_HOPS\s*:') {
    if ($PSBoundParameters.ContainsKey("TrustedProxyHops")) {
        throw "DEMO_LIMITS_TRUSTED_PROXY_HOPS is set both in $EnvironmentFile" `
            + " and by -TrustedProxyHops; keep only one."
    }
} else {
    $proxyHopsEnvironment = "DEMO_LIMITS_TRUSTED_PROXY_HOPS: `"$TrustedProxyHops`"`n"
}

# RAG needs a cloud embedding provider on Cloud Run (no local Ollama sidecar). Defaults to the
# chat provider when it can also do embeddings; GROQ cannot, so that combination must be resolved
# explicitly before deploying.
if (-not $DisableRag -and -not $EmbeddingProvider) {
    if ($Provider -eq "OPENAI" -or $Provider -eq "GEMINI") {
        $EmbeddingProvider = $Provider
    } else {
        throw "GROQ has no embedding API, so RAG needs its own provider. Pass -EmbeddingProvider" `
            + " OPENAI or GEMINI (with -EmbeddingApiKeySecret if that differs from -ApiKeySecret)," `
            + " or pass -DisableRag to deploy without project-document search."
    }
}

$image = "$Region-docker.pkg.dev/$ProjectId/ai-demo/ai-demo:latest"
$secretVariable = switch ($Provider) {
    "OPENAI" { "OPENAI_API_KEY" }
    "GROQ" { "GROQ_API_KEY" }
    "GEMINI" { "GEMINI_API_KEY" }
}

$secretMounts = @("$secretVariable=$ApiKeySecret`:latest")
# Quoted: --env-vars-file rejects an unquoted YAML true/false (it is a boolean, not a string).
$ragEnvironment = "RAG_ENABLED: `"false`"`n"
if (-not $DisableRag) {
    $embeddingSecretVariable = switch ($EmbeddingProvider) {
        "OPENAI" { "OPENAI_API_KEY" }
        "GEMINI" { "GEMINI_API_KEY" }
    }
    $embeddingBaseUrl = switch ($EmbeddingProvider) {
        "OPENAI" { "https://api.openai.com/v1" }
        "GEMINI" { "https://generativelanguage.googleapis.com/v1beta" }
    }
    $embeddingModel = switch ($EmbeddingProvider) {
        "OPENAI" { "text-embedding-3-small" }
        "GEMINI" { "text-embedding-004" }
    }
    $ragEnvironment = "RAG_ENABLED: `"true`"`nRAG_EMBEDDING_PROVIDER: $EmbeddingProvider`n" `
        + "RAG_EMBEDDING_BASE_URL: $embeddingBaseUrl`nRAG_EMBEDDING_MODEL: $embeddingModel`n" `
        + "RAG_EMBEDDING_API_KEY_ENV: $embeddingSecretVariable`n"

    if ($embeddingSecretVariable -eq $secretVariable) {
        # Same provider as chat: the secret mounted above already covers embeddings too.
    } elseif ($EmbeddingApiKeySecret) {
        $secretMounts += "$embeddingSecretVariable=$EmbeddingApiKeySecret`:latest"
    } else {
        throw "Embedding provider $EmbeddingProvider needs its own key (chat provider is" `
            + " $Provider). Pass -EmbeddingApiKeySecret with its Secret Manager secret name."
    }
}

foreach ($additionalSecret in $AdditionalSecrets) {
    $parts = $additionalSecret -split "=", 2
    if ($parts.Count -ne 2 -or -not $parts[0] -or -not $parts[1]) {
        throw "-AdditionalSecrets entries must look like ENV_VAR=secret-name, got '$additionalSecret'."
    }
    if ($secretMounts | Where-Object { $_ -like "$($parts[0])=*" }) {
        throw "$($parts[0]) is already mounted from another secret; remove it from -AdditionalSecrets."
    }
    $secretMounts += "$($parts[0])=$($parts[1])`:latest"
}

gcloud config set project $ProjectId
gcloud services enable iam.googleapis.com run.googleapis.com cloudbuild.googleapis.com artifactregistry.googleapis.com firestore.googleapis.com secretmanager.googleapis.com

# A dedicated runtime identity instead of the default Compute Engine service account, which is
# shared with Cloud Build and has no business reading this service's secrets. It gets only
# Firestore and the mounted secrets. The grants are additive and safe to repeat. If one fails
# right after the account was created (IAM propagation), run the script again.
$serviceAccountEmail = "$ServiceAccountName@$ProjectId.iam.gserviceaccount.com"
$existingAccount = gcloud iam service-accounts list --filter "email=$serviceAccountEmail" --format "value(email)"
if (-not $existingAccount) {
    gcloud iam service-accounts create $ServiceAccountName --display-name "$Service Cloud Run service"
}
$runtimeSecrets = @($secretMounts | ForEach-Object { ($_ -split "=", 2)[1] -replace ":latest$", "" }) `
    + "demo-ip-hash-salt"
foreach ($runtimeSecret in $runtimeSecrets) {
    gcloud secrets add-iam-policy-binding $runtimeSecret --member "serviceAccount:$serviceAccountEmail" `
        --role roles/secretmanager.secretAccessor --condition None | Out-Null
}
gcloud projects add-iam-policy-binding $ProjectId --member "serviceAccount:$serviceAccountEmail" `
    --role roles/datastore.user --condition None | Out-Null

# Listed instead of described: in Windows PowerShell 5.1, the error that `describe` prints for a
# missing repository ends the script before it can create it.
$repositories = gcloud artifacts repositories list --location $Region --format "value(name)"
if (-not ($repositories -match '(^|/)ai-demo$')) {
    gcloud artifacts repositories create ai-demo --repository-format docker --location $Region
}

gcloud builds submit --tag $image .
$temporaryEnvironment = New-TemporaryFile
try {
    $runtimeEnvironment = "$configuredEnvironment`nLLM_PROVIDER: $Provider`n" `
        + "GOOGLE_CLOUD_PROJECT: $ProjectId`n$proxyHopsEnvironment$ragEnvironment"
    [System.IO.File]::WriteAllText($temporaryEnvironment.FullName, $runtimeEnvironment)

    $allSecrets = ($secretMounts -join ",") + ",DEMO_IP_HASH_SALT=demo-ip-hash-salt:latest"

    gcloud run deploy $Service `
        --image $image `
        --region $Region `
        --service-account $serviceAccountEmail `
        --allow-unauthenticated `
        --port 8080 `
        --cpu 1 `
        --memory 512Mi `
        --concurrency 5 `
        --min 0 `
        --max 1 `
        --timeout 300 `
        --env-vars-file $temporaryEnvironment.FullName `
        --set-secrets $allSecrets
    # A failed deployment must not fall through to the smoke test of the previous revision.
    if ($LASTEXITCODE -ne 0) {
        throw "gcloud run deploy failed with exit code $LASTEXITCODE."
    }
} finally {
    Remove-Item -LiteralPath $temporaryEnvironment.FullName -Force -ErrorAction SilentlyContinue
}

$url = gcloud run services describe $Service --region $Region --format "value(status.url)"
& "$PSScriptRoot/smoke-test.ps1" -BaseUrl $url
Write-Host "Deployment verified at $url"
