# Day 18: baseline, documentation-grade cloud infrastructure. Scoped to
# what this project actually needs, not a generic template: a registry
# for the image infra/docker/Dockerfile builds, and a managed Postgres
# instance matching the engine already used locally. Not applied against
# a live AWS account, see infra/README.md for why and what a real
# deployment would still need on top of this (a cluster to run the app
# on, an Ingress/load balancer, the Kubernetes manifests in infra/k8s
# applied to it).

terraform {
  required_version = ">= 1.5"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = var.app_name
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}

# Holds the image infra/docker/Dockerfile builds. A real CI/CD pipeline
# (out of this day's scope, see docs/adr for the CI work already done in
# GitHub Actions) would push to this on every merge to main.
resource "aws_ecr_repository" "app" {
  name                 = var.app_name
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }
}

# Scopes inbound Postgres access to var.allowed_cidr_blocks only, never
# opened to the world. Left empty by default (see variables.tf), a real
# deployment sets this to wherever the application workload actually
# runs, an EKS node group's security group, most commonly, not a public
# CIDR.
resource "aws_security_group" "db" {
  name        = "${var.app_name}-db-${var.environment}"
  description = "Allows Postgres access from the application only"

  ingress {
    description = "PostgreSQL from the application"
    from_port   = 5432
    to_port     = 5432
    protocol    = "tcp"
    cidr_blocks = var.allowed_cidr_blocks
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# Matches the postgres:16-alpine engine already used in
# infra/docker-compose.yml, so behavior is consistent between local dev
# and this environment. skip_final_snapshot is true here only because
# this is a documentation-grade dev-sized instance, a real production
# database would want a final snapshot on destroy.
resource "aws_db_instance" "postgres" {
  identifier     = "${var.app_name}-${var.environment}"
  engine         = "postgres"
  engine_version = var.db_engine_version
  instance_class = var.db_instance_class

  allocated_storage = var.db_allocated_storage
  storage_encrypted = true

  db_name  = var.db_name
  username = var.db_username
  password = var.db_password

  vpc_security_group_ids = [aws_security_group.db.id]

  publicly_accessible = false
  skip_final_snapshot = true
}
