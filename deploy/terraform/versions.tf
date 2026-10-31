terraform {
  required_version = ">= 1.9"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 7.0"
    }
  }

  # Remote state in the same bucket as ai-agent-lab, under its own prefix: the two stacks
  # never share a state file, so they cannot lock or overwrite each other. A backend block
  # cannot use variables, so the bucket name is written out here (and the project id is a
  # constant in deploy/deploy.ps1 for the same reason).
  backend "gcs" {
    bucket = "project-996bb72d-b709-46ca-bbc-tfstate"
    prefix = "ai-demo"
  }
}

provider "google" {
  project = var.project_id
  region  = var.region
}
