# Deploying to Cloud Run

The infrastructure of `ai-demo` is described in Terraform (`deploy/terraform`) and deployed with
one command from Windows PowerShell. It replaces the former `deploy-cloud-run.ps1` (imperative
`gcloud` calls): running both against one service would make each overwrite the other's settings.

What Terraform manages in project `project-996bb72d-b709-46ca-bbc`, region `europe-west1`:

- the APIs it needs (Cloud Run, Artifact Registry, Cloud Build, Secret Manager, IAM, Firestore, ...),
- the Artifact Registry Docker repository `ai-demo` (immutable tags, cleanup policy),
- the service account `ai-demo` the service runs as, with `roles/datastore.user` and access to the
  mounted secrets only,
- the **public** Cloud Run service `ai-demo` (`allUsers` as `roles/run.invoker`; the demo limits in
  the application protect the spend),
- read access of the service account to the existing secrets `openai-api-key`, `gemini-api-key`,
  `groq-api-key` and `demo-ip-hash-salt`. The secrets themselves are **not** owned by Terraform;
  their values never pass through it,
- the state in a GCS bucket (backend `gcs`, prefix `ai-demo`).

## Working together with ai-agent-lab

`ai-agent-lab` (a separate repository) runs in the same project, from its own Terraform stack.
The two stacks are independent but fit together:

| Concern | How it is arranged |
| --- | --- |
| State | Same bucket, different prefix (`ai-demo` / `ai-agent-lab`): separate state files and locks. |
| Resources | Every resource has exactly one owner. Nothing is declared in both stacks except the API enablements, which are idempotent and never switched off (`disable_on_destroy = false`). |
| Secrets | `openai-api-key` is read by both stacks; each grants only its own service account access (additive `*_iam_member`), so neither removes the other's access. |
| Build permissions | The project-wide grants for `gcloud builds submit` (`logging.logWriter`, source bucket reader) are owned by the ai-agent-lab stack; this stack only manages the push role on its own repository. |
| ai-demo calls the agent | With `agent_lab_enabled = true` the agent's URL (also the ID-token audience) is looked up from the deployed `ai-agent-lab` service, not copied by hand. |
| Service account name | ai-agent-lab refers to `ai-demo@<project>.iam.gserviceaccount.com` by name (`ai_demo_service_account_email`), while `service_name` here is also the service account id. Do not rename it without changing that variable there; if the account is ever deleted and recreated, run ai-agent-lab's `apply` again so its invoker grant points at the new principal. |
| The agent calls ai-demo | ai-agent-lab grants `roles/run.invoker` on its service to the `ai-demo` service account (the `service_account_email` output here) and may call the public `/api/llm/providers` (`service_url` output here as `ai_demo_base_url` there). |

Order on a project where neither exists (the two have a circular dependency: ai-demo's builds need
the build grants of ai-agent-lab, whose invoker grant needs ai-demo's service account):

1. `ai-agent-lab`: `deploy.ps1` creates the build grants in its first phase. Its first build creates
   the `<project>_cloudbuild` source bucket, which the script cannot grant access to before it exists,
   so the build typically fails on `storage.objects.get`: run it again. It then stops at the invoker
   grant because the `ai-demo` service account does not exist yet;
2. `ai-demo`: set `agent_lab_enabled = false` in `terraform.tfvars` (the agent does not exist yet), then `deploy.ps1`;
3. `ai-agent-lab`: `deploy.ps1` again, now it completes;
4. `ai-demo`: remove that line (agent mode is on by default) and run `deploy.ps1` again.

Destroying the ai-agent-lab stack removes the build grants and so breaks ai-demo's builds until they
are granted again. Moving the two shared grants into a small bootstrap stack owned by neither
application would remove both the circle and this coupling; it is not done because the current
project already has everything.

## One-off manual steps

1. The Terraform state bucket `project-996bb72d-b709-46ca-bbc-tfstate` is shared with ai-agent-lab; if
   it does not exist yet, create it as described in that project's `deploy/README.md`.
2. Log in twice: Terraform uses the Application Default Credentials, `gcloud builds submit` the
   normal gcloud login:

   ```powershell
   gcloud auth login
   gcloud auth application-default login
   ```

   The account needs to be able to manage the project (e.g. Owner).
3. Create the secrets in Secret Manager (values are added by hand): the key of every provider the
   service should offer (`openai-api-key`, `gemini-api-key`, `groq-api-key`) and a long random
   `demo-ip-hash-salt`. Terraform fails with a clear error if one is missing.
4. Create a Firestore Native-mode database `(default)` in `europe-west1` (it holds the global usage
   counters). Terraform deliberately does not manage it: it contains data.
5. If the project still has the GitHub-connected Cloud Build trigger that deploys `ai-demo` on every
   push to `main`, disable or delete it (Cloud Build -> Triggers). It builds into another repository and
   runs `gcloud run services update --image` against the same service, which would silently replace
   the image Terraform deployed.

Secrets are mounted as version `latest`, which Cloud Run resolves when a revision starts. After a key
is rotated (including `openai-api-key`, shared with ai-agent-lab), run `deploy.ps1` again so a new
revision picks up the new version.

### Migrating a service that was created by the old script

The service, service account, repository and IAM grants already exist, so Terraform must adopt
them instead of creating them. `deploy/terraform/imports.tf` does that with `import` blocks: the
first `deploy.ps1` shows them in the plan as "will be imported". After the first successful apply,
delete `imports.tf`: on a project where the resources do not exist the imports would fail.

Also after the migration, remove the leftovers of the manual deployment from the default Compute
Engine account (`<project number>-compute@developer.gserviceaccount.com`). It runs Cloud Build for
both services and has no business reading the provider keys or the salt; no Cloud Run service runs
as it any more. No stack manages these grants. Do **not** touch its `logging.logWriter` and
Artifact Registry roles or its `storage.objectViewer` on the `<project>_cloudbuild` bucket, builds need
them. (It has no project-wide `datastore.user`, and it does have `secretAccessor` on all four
secrets, so the loop below should not report errors.)

```powershell
foreach ($secret in 'openai-api-key', 'gemini-api-key', 'groq-api-key', 'demo-ip-hash-salt') {
  gcloud secrets remove-iam-policy-binding $secret --project project-996bb72d-b709-46ca-bbc `
    --member serviceAccount:747887321067-compute@developer.gserviceaccount.com `
    --role roles/secretmanager.secretAccessor
}
```

## Deploy and update

```powershell
.\deploy\deploy.ps1
```

The same command creates everything on the first run and updates it afterwards. It:

1. runs `terraform init` and `terraform apply` for only what the build needs (these must exist
   before an image can be pushed): the APIs, the Artifact Registry repository and the build account's
   push role,
2. builds the image with Cloud Build from the repository root and pushes it to Artifact Registry,
   tagged with the git commit SHA (uncommitted changes in the files that go into the image --
   `src/`, `frontend/`, `pom.xml`, `Dockerfile` -- give a `<sha>-dirty-<timestamp>` tag and a warning),
3. runs `terraform apply` for the whole infrastructure with that tag, which creates/updates the
   Cloud Run service. It prints the outputs, including `service_url`,
4. runs `smoke-test.ps1` against the deployed service (read-only; `-SkipSmokeTest` skips it).

Terraform shows its plan and asks for confirmation before each apply; `-AutoApprove` skips that.
Other parameters: `-Region`, `-ImageTag` (with `-SkipBuild` to redeploy an image that is already
pushed, e.g. a rollback). The project id is a constant in the script, not a parameter, because the
state bucket in `terraform/versions.tf` belongs to that project.

Tags are **immutable**: a pushed tag can never be overwritten, neither by this script nor by anyone
else. A cleanup policy keeps the newest `images_to_keep` images (10) and deletes older ones after 30
days, so a rollback works only to an image that is still there.

## Configuration

Settings go to `deploy/terraform/terraform.tfvars` (git-ignored, start from `terraform.tfvars.example`);
`deploy.ps1` picks it up automatically. The defaults reproduce the production configuration.

| Variable | Meaning |
| --- | --- |
| `agent_lab_enabled` | Agent mode: forward chat requests to the private `ai-agent-lab` service (`AGENT_LAB_*`). The URL is looked up from the deployed service, so it must exist; set `false` until then. Default `true` (the production setting, so a deploy without a local `terraform.tfvars` keeps agent mode). |
| `llm_provider` | Default chat provider: `OPENAI` (default), `GROQ` or `GEMINI`. |
| `rag_embedding_provider` | `OPENAI` (default) or `GEMINI`; empty turns RAG off. GROQ has no embeddings. |
| `provider_secret_ids` | `ENV_VAR = secret id` of the provider keys to mount; every provider the service should offer. |
| `trusted_proxy_hops` | `DEMO_LIMITS_TRUSTED_PROXY_HOPS`: `1` for Cloud Run alone (default), `2` behind an external load balancer. |
| `extra_env` | Plain variables added to and overriding the defaults in `cloud_run.tf`, e.g. the other `DEMO_LIMITS_*`. Rejected: names ending in `_API_KEY` or `_SALT`, and the ones the typed variables above control (`LLM_PROVIDER`, `RAG_ENABLED`/`RAG_EMBEDDING_*`, `AGENT_LAB_ENABLED`/`BASE_URL`/`AUTHENTICATION`, `DEMO_LIMITS_TRUSTED_PROXY_HOPS`, `GOOGLE_CLOUD_PROJECT`). |
| `max_instances`, `request_timeout_seconds` | `1` (the concurrent-stream limit is per instance, see PRD) and `300` s. |
| `images_to_keep` | Newest images kept in Artifact Registry (10). |

The application's own defaults and the full list of settings are in the main `README.md`.

## Not covered by Terraform

- the Firestore database and the secret values (see the manual steps),
- the custom domain / load balancer in front of the service, if any,
- project-wide Cloud Build permissions (owned by the ai-agent-lab stack).
