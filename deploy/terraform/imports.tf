# ONE-OFF MIGRATION. These resources were created by the former deploy-cloud-run.ps1 (gcloud).
# The import blocks adopt them into the Terraform state instead of failing with "already
# exists". Delete this file after the first successful `terraform apply`: on a project where
# the resources do not exist the imports would fail.
#
# Not imported on purpose:
#   - the Firestore database (holds the usage counters; created by hand, see deploy/README.md),
#   - the API enablements (enabling an enabled API is a no-op),
#   - the secrets (values were added by hand; only read, see secrets.tf).

locals {
  service_account_member = "serviceAccount:${var.service_name}@${var.project_id}.iam.gserviceaccount.com"
}

import {
  to = google_service_account.app
  id = "projects/${var.project_id}/serviceAccounts/${var.service_name}@${var.project_id}.iam.gserviceaccount.com"
}

import {
  to = google_project_iam_member.firestore_user
  id = "${var.project_id} roles/datastore.user ${local.service_account_member}"
}

import {
  to = google_artifact_registry_repository.images
  id = "projects/${var.project_id}/locations/${var.region}/repositories/${var.artifact_registry_repository}"
}

import {
  for_each = local.secret_ids

  to = google_secret_manager_secret_iam_member.access[each.value]
  id = "projects/${var.project_id}/secrets/${each.value} roles/secretmanager.secretAccessor ${local.service_account_member}"
}

import {
  to = google_cloud_run_v2_service.app
  id = "projects/${var.project_id}/locations/${var.region}/services/${var.service_name}"
}

import {
  to = google_cloud_run_v2_service_iam_member.public_invoker
  id = "projects/${var.project_id}/locations/${var.region}/services/${var.service_name} roles/run.invoker allUsers"
}
