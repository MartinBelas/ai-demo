locals {
  image = "${local.image_repository_url}:${var.image_tag}"

  rag_enabled = var.rag_embedding_provider != ""

  embedding = {
    OPENAI = {
      base_url = "https://api.openai.com/v1"
      model    = "text-embedding-3-small"
    }
    GEMINI = {
      base_url = "https://generativelanguage.googleapis.com/v1beta"
      model    = "text-embedding-004"
    }
  }

  # The URL of the private agent service is read from the deployed service instead of being
  # copied by hand; it is also the audience of the Google ID token the app sends.
  agent_lab_url = var.agent_lab_enabled ? data.google_cloud_run_v2_service.agent_lab[0].uri : null

  env = merge(
    {
      APP_INTERFACE                          = "http"
      OLLAMA_ENABLED                         = "false"
      LLM_PROVIDER                           = var.llm_provider
      GOOGLE_CLOUD_PROJECT                   = var.project_id
      FIRESTORE_DATABASE_ID                  = "(default)"
      DEMO_LIMITS_ENABLED                    = "true"
      DEMO_LIMITS_FIRESTORE_ENABLED          = "true"
      DEMO_LIMITS_DAILY_REQUESTS             = "1000"
      DEMO_LIMITS_HOURLY_REQUESTS_PER_IP     = "20"
      DEMO_LIMITS_CONCURRENT_STREAMS         = "5"
      DEMO_LIMITS_MAX_INPUT_CHARACTERS       = "20000"
      DEMO_LIMITS_MAX_HISTORY_MESSAGES       = "10"
      DEMO_LIMITS_MAX_OUTPUT_TOKENS_PER_CALL = "1500"
      DEMO_LIMITS_TRUSTED_PROXY_HOPS         = tostring(var.trusted_proxy_hops)
      RAG_ENABLED                            = tostring(local.rag_enabled)
    },
    local.rag_enabled ? {
      RAG_EMBEDDING_PROVIDER    = var.rag_embedding_provider
      RAG_EMBEDDING_BASE_URL    = local.embedding[var.rag_embedding_provider].base_url
      RAG_EMBEDDING_MODEL       = local.embedding[var.rag_embedding_provider].model
      RAG_EMBEDDING_API_KEY_ENV = "${var.rag_embedding_provider}_API_KEY"
    } : {},
    var.agent_lab_enabled ? {
      AGENT_LAB_ENABLED        = "true"
      AGENT_LAB_BASE_URL       = local.agent_lab_url
      AGENT_LAB_AUTHENTICATION = "GOOGLE_ID_TOKEN"
    } : {},
    var.extra_env,
  )

  secret_env = merge(
    var.provider_secret_ids,
    { DEMO_IP_HASH_SALT = var.ip_hash_salt_secret_id },
  )
}

data "google_cloud_run_v2_service" "agent_lab" {
  count = var.agent_lab_enabled ? 1 : 0

  name     = var.agent_lab_service_name
  location = var.region
}

resource "google_cloud_run_v2_service" "app" {
  name     = var.service_name
  location = var.region

  ingress             = "INGRESS_TRAFFIC_ALL"
  deletion_protection = false

  template {
    service_account = google_service_account.app.email
    timeout         = "${var.request_timeout_seconds}s"

    max_instance_request_concurrency = 5

    scaling {
      min_instance_count = 0
      max_instance_count = var.max_instances
    }

    containers {
      image = local.image

      ports {
        container_port = 8080
      }

      resources {
        limits = {
          cpu    = "1"
          memory = "512Mi"
        }

        # Request-based billing: CPU only while a request is being handled, with a boost
        # while the JVM starts.
        cpu_idle          = true
        startup_cpu_boost = true
      }

      startup_probe {
        http_get {
          path = "/api/health"
        }
        # 5 s x 48 = 240 s, the window the former TCP probe allowed for a cold JVM start.
        timeout_seconds   = 3
        period_seconds    = 5
        failure_threshold = 48
      }

      dynamic "env" {
        for_each = local.env
        content {
          name  = env.key
          value = env.value
        }
      }

      dynamic "env" {
        for_each = local.secret_env
        content {
          name = env.key
          value_source {
            secret_key_ref {
              secret  = env.value
              version = "latest"
            }
          }
        }
      }
    }
  }

  # The revision cannot start before the service account may read the secrets, and
  # Firestore access is needed from the first request on.
  depends_on = [
    google_secret_manager_secret_iam_member.access,
    google_project_iam_member.firestore_user,
  ]

  lifecycle {
    ignore_changes = [client, client_version]

    precondition {
      condition     = contains(keys(var.provider_secret_ids), "${var.llm_provider}_API_KEY")
      error_message = "provider_secret_ids has no secret for llm_provider ${var.llm_provider} (${var.llm_provider}_API_KEY)."
    }

    precondition {
      condition     = !local.rag_enabled || contains(keys(var.provider_secret_ids), "${var.rag_embedding_provider}_API_KEY")
      error_message = "provider_secret_ids has no secret for rag_embedding_provider ${var.rag_embedding_provider} (${var.rag_embedding_provider}_API_KEY)."
    }
  }
}

# The demo is public by design (PRD): anyone may open the page and call the API; the
# demo limits protect the spend. Additive, other invokers are not touched.
resource "google_cloud_run_v2_service_iam_member" "public_invoker" {
  project  = google_cloud_run_v2_service.app.project
  location = google_cloud_run_v2_service.app.location
  name     = google_cloud_run_v2_service.app.name
  role     = "roles/run.invoker"
  member   = "allUsers"
}
