variable "project_id" {
  description = "GCP project (the same one that runs ai-agent-lab)."
  type        = string
  default     = "project-996bb72d-b709-46ca-bbc"
}

variable "region" {
  description = "Region of Cloud Run and Artifact Registry. Firestore lives in the same region."
  type        = string
  default     = "europe-west1"
}

variable "service_name" {
  description = "Name of the Cloud Run service (and of the image in Artifact Registry and of the service account)."
  type        = string
  default     = "ai-demo"

  # Cloud Run allows up to 49 characters, but the name is also the service account
  # id, which is limited to 6-30 characters.
  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{4,28}[a-z0-9]$", var.service_name))
    error_message = "service_name must be 6-30 characters: lowercase letters, digits and hyphens, starting with a letter and not ending with a hyphen (it is used as the Cloud Run service name and as the service account id)."
  }
}

variable "image_tag" {
  description = "Tag of the image to run, normally the commit SHA. Set by deploy/deploy.ps1."
  type        = string

  validation {
    condition     = can(regex("^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$", var.image_tag))
    error_message = "image_tag must be a valid Docker tag: at most 128 characters from A-Z a-z 0-9 _ . -, not starting with a period or a hyphen."
  }
}

variable "artifact_registry_repository" {
  description = "Name of the Docker repository in Artifact Registry."
  type        = string
  default     = "ai-demo"
}

variable "images_to_keep" {
  description = "Number of most recent images kept in Artifact Registry; older ones are deleted after 30 days. Rollbacks (-SkipBuild -ImageTag) work only to images that are still kept."
  type        = number
  default     = 10

  validation {
    condition     = var.images_to_keep >= 1 && var.images_to_keep == floor(var.images_to_keep)
    error_message = "images_to_keep must be a whole number of at least 1."
  }
}

variable "llm_provider" {
  description = "Default chat provider (LLM_PROVIDER)."
  type        = string
  default     = "OPENAI"

  validation {
    condition     = contains(["OPENAI", "GROQ", "GEMINI"], var.llm_provider)
    error_message = "llm_provider must be OPENAI, GROQ or GEMINI."
  }
}

variable "rag_embedding_provider" {
  description = "Embedding provider of RAG. There is no Ollama on Cloud Run, so it must be a cloud provider; GROQ has no embedding API. Empty disables RAG (project-document search)."
  type        = string
  default     = "OPENAI"

  validation {
    condition     = contains(["", "OPENAI", "GEMINI"], var.rag_embedding_provider)
    error_message = "rag_embedding_provider must be OPENAI, GEMINI or empty (RAG off)."
  }
}

variable "provider_secret_ids" {
  description = <<-EOT
    Existing Secret Manager secrets with the provider API keys, as ENV_VAR = secret id. Every
    provider the service should offer must be listed; the keys of llm_provider and of
    rag_embedding_provider are required. Terraform only reads these secrets and grants access;
    the values are added by hand and never pass through Terraform.
  EOT
  type        = map(string)
  default = {
    OPENAI_API_KEY = "openai-api-key"
    GEMINI_API_KEY = "gemini-api-key"
    GROQ_API_KEY   = "groq-api-key"
  }

  validation {
    condition     = alltrue([for name in keys(var.provider_secret_ids) : contains(["OPENAI_API_KEY", "GEMINI_API_KEY", "GROQ_API_KEY"], name)])
    error_message = "provider_secret_ids may only contain OPENAI_API_KEY, GEMINI_API_KEY and GROQ_API_KEY."
  }
}

variable "ip_hash_salt_secret_id" {
  description = "Existing Secret Manager secret with the salt for hashing client addresses (DEMO_IP_HASH_SALT)."
  type        = string
  default     = "demo-ip-hash-salt"
}

variable "trusted_proxy_hops" {
  description = "DEMO_LIMITS_TRUSTED_PROXY_HOPS: 1 for Cloud Run alone, 2 behind an external Application Load Balancer. 0 ignores X-Forwarded-For and puts every client into one shared limit."
  type        = number
  default     = 1

  validation {
    condition     = var.trusted_proxy_hops >= 0 && var.trusted_proxy_hops <= 10 && var.trusted_proxy_hops == floor(var.trusted_proxy_hops)
    error_message = "trusted_proxy_hops must be a whole number between 0 and 10."
  }
}

variable "agent_lab_enabled" {
  description = "Agent mode: forward chat requests to the private ai-agent-lab service. Its URL is read from the deployed service, so ai-agent-lab must be deployed first (set false until then). The default is the production setting, so a deploy from a checkout without terraform.tfvars does not switch agent mode off."
  type        = bool
  default     = true
}

variable "agent_lab_service_name" {
  description = "Name of the ai-agent-lab Cloud Run service (same project and region)."
  type        = string
  default     = "ai-agent-lab"
}

variable "extra_env" {
  description = "Plain environment variables added to (and overriding) the defaults in cloud_run.tf, e.g. the DEMO_LIMITS_* values."
  type        = map(string)
  default     = {}

  validation {
    condition     = alltrue([for name in keys(var.extra_env) : !endswith(upper(name), "_API_KEY") && !endswith(upper(name), "_SALT")])
    error_message = "extra_env must not contain secrets (names ending in _API_KEY or _SALT): plain variables end up in the Terraform state and in the Cloud Run configuration. Secrets belong in Secret Manager (see provider_secret_ids)."
  }

  # These are derived from typed variables and checked by preconditions; setting them here
  # would bypass both and could leave the service in a configuration that cannot start.
  validation {
    condition = length(setintersection(toset(keys(var.extra_env)), toset([
      "LLM_PROVIDER",
      "GOOGLE_CLOUD_PROJECT",
      "DEMO_LIMITS_TRUSTED_PROXY_HOPS",
      "RAG_ENABLED",
      "RAG_EMBEDDING_PROVIDER",
      "RAG_EMBEDDING_BASE_URL",
      "RAG_EMBEDDING_MODEL",
      "RAG_EMBEDDING_API_KEY_ENV",
      "AGENT_LAB_ENABLED",
      "AGENT_LAB_BASE_URL",
      "AGENT_LAB_AUTHENTICATION",
    ]))) == 0
    error_message = "extra_env must not set LLM_PROVIDER, GOOGLE_CLOUD_PROJECT, DEMO_LIMITS_TRUSTED_PROXY_HOPS, RAG_ENABLED, RAG_EMBEDDING_* or AGENT_LAB_ENABLED/BASE_URL/AUTHENTICATION: use llm_provider, trusted_proxy_hops, rag_embedding_provider and agent_lab_enabled instead."
  }
}

variable "max_instances" {
  description = "Upper bound of instances. The concurrent-stream limit is counted in the memory of one instance (see PRD.md), so it is a global limit only with 1."
  type        = number
  default     = 1

  validation {
    condition     = var.max_instances >= 1 && var.max_instances == floor(var.max_instances)
    error_message = "max_instances must be a whole number of at least 1."
  }
}

variable "request_timeout_seconds" {
  description = "Cloud Run request timeout; it bounds the longest streamed answer."
  type        = number
  default     = 300

  validation {
    condition     = var.request_timeout_seconds >= 1 && var.request_timeout_seconds <= 3600 && var.request_timeout_seconds == floor(var.request_timeout_seconds)
    error_message = "request_timeout_seconds must be a whole number between 1 and 3600 (the Cloud Run maximum)."
  }
}
