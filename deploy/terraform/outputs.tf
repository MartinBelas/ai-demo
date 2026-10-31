output "image_repository_url" {
  description = "Image name without the tag."
  value       = local.image_repository_url
}

output "service_url" {
  description = "Public URL of the service; use it as ai_demo_base_url in the ai-agent-lab stack."
  value       = google_cloud_run_v2_service.app.uri
}

output "service_account_email" {
  description = "Runtime service account; ai-agent-lab grants it roles/run.invoker on the agent."
  value       = google_service_account.app.email
}

output "agent_lab_url" {
  description = "URL of the ai-agent-lab service this deployment calls (null when agent mode is off)."
  value       = local.agent_lab_url
}
