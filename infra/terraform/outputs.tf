# Day 18: values a deployment pipeline or the Kubernetes manifests in
# infra/k8s would actually need after apply, the ECR URL to push/pull
# the image, and the RDS endpoint to build SPRING_DATASOURCE_URL from.
# Deliberately no output for the database password, it's an input
# variable, not something Terraform should ever echo back out.

output "ecr_repository_url" {
  description = "Push the image infra/docker/Dockerfile builds here."
  value       = aws_ecr_repository.app.repository_url
}

output "db_endpoint" {
  description = "RDS endpoint (host:port). Combine with var.db_name to build SPRING_DATASOURCE_URL."
  value       = aws_db_instance.postgres.endpoint
}

output "db_instance_id" {
  description = "RDS instance identifier, useful for looking it up in the AWS console/CLI."
  value       = aws_db_instance.postgres.id
}
