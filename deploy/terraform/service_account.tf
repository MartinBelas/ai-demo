# Identity of the running service. Not the default Compute Engine account, which is shared
# with Cloud Build. It gets Firestore (the demo-limit counters) and read access to the
# secrets it mounts (see secrets.tf), nothing else.
resource "google_service_account" "app" {
  account_id   = var.service_name
  display_name = "${var.service_name} Cloud Run service"

  depends_on = [google_project_service.apis]
}

resource "google_project_iam_member" "firestore_user" {
  project = var.project_id
  role    = "roles/datastore.user"
  member  = "serviceAccount:${google_service_account.app.email}"
}
