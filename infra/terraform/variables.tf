# Day 18: baseline variables for the cloud resources Sentinel actually
# needs, an ECR repository for the image infra/docker/Dockerfile already
# builds, and an RDS PostgreSQL instance matching the same engine used
# in infra/docker-compose.yml for local dev. Nothing here has a sensitive
# default, matching NFR-2 in docs/SRS.md (no secrets committed to the
# repo).

variable "aws_region" {
  description = "AWS region to provision resources in."
  type        = string
  default     = "eu-west-1"
}

variable "environment" {
  description = "Deployment environment name, used to tag and name resources (e.g. dev, staging, prod)."
  type        = string
  default     = "dev"
}

variable "app_name" {
  description = "Base name used for the ECR repository and resource tags."
  type        = string
  default     = "sentinel-secscan"
}

variable "db_engine_version" {
  description = "PostgreSQL engine version. Matches the postgres:16-alpine image used in infra/docker-compose.yml."
  type        = string
  default     = "16"
}

variable "db_instance_class" {
  description = "RDS instance class. Smallest burstable class, this project's actual load never justifies more."
  type        = string
  default     = "db.t3.micro"
}

variable "db_allocated_storage" {
  description = "Allocated storage for the RDS instance, in GiB."
  type        = number
  default     = 20
}

variable "db_name" {
  description = "Database name. Matches the default already used in application.properties and infra/.env.example."
  type        = string
  default     = "sentinel"
}

variable "db_username" {
  description = "Master username for the RDS instance."
  type        = string
  default     = "sentinel"
}

variable "db_password" {
  description = "Master password for the RDS instance. No default on purpose, must be supplied via TF_VAR_db_password or a .tfvars file that is never committed, the same 'no secrets committed to the repo' rule application.properties already follows for sentinel.jwt.secret."
  type        = string
  sensitive   = true
}

variable "allowed_cidr_blocks" {
  description = "CIDR blocks allowed to reach the database on port 5432. Defaults to nothing, must be scoped explicitly to wherever the application actually runs (e.g. the EKS node/pod security group in a real deployment), never left open to 0.0.0.0/0."
  type        = list(string)
  default     = []
}
