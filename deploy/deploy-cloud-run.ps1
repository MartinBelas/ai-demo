param(
    [Parameter(Mandatory = $true)][string]$ProjectId,
    [string]$Region = "europe-west1",
    [string]$Service = "ai-demo",
    [ValidateSet("OPENAI", "GROQ", "GEMINI")][string]$Provider = "OPENAI",
    [Parameter(Mandatory = $true)][string]$ApiKeySecret,
    [ValidateSet("", "OPENAI", "GEMINI")][string]$EmbeddingProvider = "",
    [string]$EmbeddingApiKeySecret,
    [switch]$DisableRag,
    [string]$EnvironmentFile = "$PSScriptRoot/cloudrun.env.yaml"
)

$ErrorActionPreference = "Stop"
if (-not (Test-Path -LiteralPath $EnvironmentFile)) {
    throw "Environment file not found: $EnvironmentFile. Copy cloudrun.env.yaml.example first."
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
$ragEnvironment = "RAG_ENABLED: false`n"
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
    $ragEnvironment = "RAG_ENABLED: true`nRAG_EMBEDDING_PROVIDER: $EmbeddingProvider`n" `
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
    $runtimeEnvironment = "$configuredEnvironment`nLLM_PROVIDER: $Provider`n" `
        + "GOOGLE_CLOUD_PROJECT: $ProjectId`n$ragEnvironment"
    [System.IO.File]::WriteAllText($temporaryEnvironment.FullName, $runtimeEnvironment)

    $allSecrets = ($secretMounts -join ",") + ",DEMO_IP_HASH_SALT=demo-ip-hash-salt:latest"

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
        --set-secrets $allSecrets
} finally {
    Remove-Item -LiteralPath $temporaryEnvironment.FullName -Force -ErrorAction SilentlyContinue
}

$url = gcloud run services describe $Service --region $Region --format "value(status.url)"
& "$PSScriptRoot/smoke-test.ps1" -BaseUrl $url
Write-Host "Deployment verified at $url"
