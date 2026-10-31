locals {
  apis = [
    "serviceusage.googleapis.com",
    "cloudresourcemanager.googleapis.com",
    "iam.googleapis.com",
    "run.googleapis.com",
    "artifactregistry.googleapis.com",
    "cloudbuild.googleapis.com",
    "secretmanager.googleapis.com",
    "firestore.googleapis.com",
  ]
}

# ai-agent-lab enables most of the same APIs in its own stack. Enabling an API that is
# already on is a no-op, so the overlap is harmless as long as neither stack switches APIs
# off again.
resource "google_project_service" "apis" {
  for_each = toset(local.apis)

  service = each.value

  disable_on_destroy = false
}
