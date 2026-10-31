<#
.SYNOPSIS
Deploys ai-demo to Cloud Run with one command: infrastructure (Terraform) and image (Cloud Build).

.DESCRIPTION
1. terraform apply of only what the build needs: the APIs, the Artifact Registry repository
   (it must exist before the first image can be pushed) and the build service account's push role.
2. Cloud Build builds the image from the repository root and pushes it, tagged with the commit SHA.
3. terraform apply of the whole infrastructure with that image tag.
4. Read-only smoke checks of the deployed service.

Terraform shows its plan and asks for confirmation before each apply; pass -AutoApprove to skip that.
One-off prerequisites (state bucket, gcloud logins, secrets, Firestore) are described in deploy/README.md.

.EXAMPLE
.\deploy\deploy.ps1
#>
[CmdletBinding()]
param(
    [string]$Region = 'europe-west1',
    [string]$ServiceName = 'ai-demo',
    [string]$Repository = 'ai-demo',
    # Overrides the tag derived from git, e.g. to redeploy an image that is already pushed.
    [string]$ImageTag,
    # Skips the image build; use together with -ImageTag.
    [switch]$SkipBuild,
    [switch]$SkipSmokeTest,
    [switch]$AutoApprove
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# Not a parameter: the Terraform state bucket in terraform/versions.tf (backend "gcs") is
# tied to this project, and a backend block cannot use variables. Deploying to another
# project means changing both places (and creating that project's state bucket).
$ProjectId = 'project-996bb72d-b709-46ca-bbc'

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$terraformDirectory = Join-Path $PSScriptRoot 'terraform'

function Invoke-Native {
    param([string]$Command, [string[]]$Arguments)
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "'$Command $($Arguments -join ' ')' failed with exit code $LASTEXITCODE."
    }
}

function Get-NativeOutput {
    param([string]$Command, [string[]]$Arguments)
    $output = & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "'$Command $($Arguments -join ' ')' failed with exit code $LASTEXITCODE."
    }
    return ($output | Out-String).Trim()
}

foreach ($tool in 'terraform', 'gcloud', 'git') {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
        throw "'$tool' was not found on PATH."
    }
}

# Terraform uses Application Default Credentials, gcloud uses its own login (for Cloud Build).
$adcFile = Join-Path $env:APPDATA 'gcloud\application_default_credentials.json'
if (-not $env:GOOGLE_APPLICATION_CREDENTIALS -and -not (Test-Path $adcFile)) {
    throw 'No Application Default Credentials found. Run: gcloud auth application-default login'
}
if (-not (Get-NativeOutput gcloud @('auth', 'list', '--filter=status:ACTIVE', '--format=value(account)'))) {
    throw 'gcloud is not logged in. Run: gcloud auth login'
}

# The image tag is the commit SHA, so every running revision can be traced to its code.
# Uncommitted changes would make that a lie, so such a build gets a distinct tag. Only the
# files that end up in the image count (see Dockerfile); changes in deploy/, docs or tests
# do not make the image dirty.
if (-not $ImageTag) {
    if ($SkipBuild) {
        throw '-SkipBuild needs -ImageTag (the tag of an image that is already pushed).'
    }
    $sha = Get-NativeOutput git @('-C', $repositoryRoot, 'rev-parse', '--short=12', 'HEAD')
    $ImageTag = $sha
    if (Get-NativeOutput git @('-C', $repositoryRoot, 'status', '--porcelain', '--', 'src', 'frontend', 'pom.xml', 'Dockerfile')) {
        $ImageTag = "$sha-dirty-$(Get-Date -Format 'yyyyMMddHHmmss')"
        Write-Warning "Uncommitted changes in the working tree; the image is tagged $ImageTag, not $sha."
    }
}

$image = "$Region-docker.pkg.dev/$ProjectId/$Repository/${ServiceName}:$ImageTag"
$terraformVariables = @(
    "-var=project_id=$ProjectId",
    "-var=region=$Region",
    "-var=service_name=$ServiceName",
    "-var=artifact_registry_repository=$Repository",
    "-var=image_tag=$ImageTag"
)
$applyFlags = @()
if ($AutoApprove) { $applyFlags += '-auto-approve' }

Push-Location $terraformDirectory
try {
    # Everything the build needs before it can start: the APIs, the repository to push to
    # and the build service account's permission to push (see cloud_build.tf).
    $buildPrerequisites = @(
        '-target=google_project_service.apis',
        '-target=google_artifact_registry_repository.images',
        '-target=google_artifact_registry_repository_iam_member.build_image_writer'
    )

    Write-Host "== 1/3 APIs, Artifact Registry and build permissions ==" -ForegroundColor Cyan
    Invoke-Native terraform @('init', '-input=false')
    Invoke-Native terraform (@('apply') + $buildPrerequisites + $terraformVariables + $applyFlags)

    if ($SkipBuild) {
        Write-Host "== 2/3 Image build skipped, using $image ==" -ForegroundColor Cyan
    } else {
        Write-Host "== 2/3 Building $image ==" -ForegroundColor Cyan
        Invoke-Native gcloud @('builds', 'submit', $repositoryRoot, '--project', $ProjectId, '--tag', $image)
    }

    Write-Host "== 3/3 Cloud Run service and the rest of the infrastructure ==" -ForegroundColor Cyan
    Invoke-Native terraform (@('apply') + $terraformVariables + $applyFlags)

    Write-Host ''
    Write-Host "Deployed $image" -ForegroundColor Green
    Invoke-Native terraform @('output')

    if (-not $SkipSmokeTest) {
        $serviceUrl = Get-NativeOutput terraform @('output', '-raw', 'service_url')
        & (Join-Path $PSScriptRoot 'smoke-test.ps1') -BaseUrl $serviceUrl
    }
} finally {
    Pop-Location
}
