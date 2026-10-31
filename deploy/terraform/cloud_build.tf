# `gcloud builds submit` runs the build as the default Compute Engine service account.
# Only the grant on this stack's own repository is managed here.
#
# The two project-wide grants the build needs (roles/logging.logWriter on the project and
# roles/storage.objectViewer on the PROJECT_cloudbuild source bucket) are owned by the
# ai-agent-lab stack (its cloud_build.tf). They are deliberately not repeated here: the same
# binding in two states means destroying one stack would silently break the other's builds.
# On a project without ai-agent-lab grant those two roles to the account once by hand.
data "google_project" "this" {
  project_id = var.project_id

  depends_on = [google_project_service.apis]
}

locals {
  build_service_account = "serviceAccount:${data.google_project.this.number}-compute@developer.gserviceaccount.com"
}

# Pushes the image. Additive (*_iam_member): other writers are left alone.
resource "google_artifact_registry_repository_iam_member" "build_image_writer" {
  project    = google_artifact_registry_repository.images.project
  location   = google_artifact_registry_repository.images.location
  repository = google_artifact_registry_repository.images.name
  role       = "roles/artifactregistry.writer"
  member     = local.build_service_account
}
