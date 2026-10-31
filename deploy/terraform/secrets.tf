# The secrets hold values that were added by hand (provider API keys, the IP-hash salt) and
# ai-agent-lab reads the OpenAI one as well. Terraform therefore only reads them (to get the
# id) and lets this service's account access them; it neither owns them nor touches their
# versions, so no value ever lands in the state. The IAM members are additive: the access of
# ai-agent-lab and of the Compute Engine account stays untouched.
locals {
  secret_ids = toset(concat(values(var.provider_secret_ids), [var.ip_hash_salt_secret_id]))
}

data "google_secret_manager_secret" "mounted" {
  for_each = local.secret_ids

  secret_id = each.value

  depends_on = [google_project_service.apis]
}

resource "google_secret_manager_secret_iam_member" "access" {
  for_each = data.google_secret_manager_secret.mounted

  secret_id = each.value.secret_id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.app.email}"
}
