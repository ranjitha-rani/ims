output "alb_url" {
  description = "Public REST API base URL for VITE_API_BASE_URL."
  value       = "${var.certificate_arn == "" ? "http" : "https"}://${aws_lb.api.dns_name}/api"
}

output "ecr_repository_url" {
  description = "API ECR repository URL."
  value       = aws_ecr_repository.api.repository_url
}

output "ecs_cluster_name" {
  description = "ECS cluster used by deployment automation."
  value       = aws_ecs_cluster.main.name
}

output "ecs_service_name" {
  description = "ECS API service name."
  value       = aws_ecs_service.api.name
}

output "database_secret_arn" {
  description = "Secrets Manager ARN containing PostgreSQL connection values."
  value       = aws_secretsmanager_secret.database.arn
}

output "runtime_secret_arn" {
  description = "Secrets Manager ARN containing JWT and bootstrap administrator values."
  value       = aws_secretsmanager_secret.runtime.arn
}

output "database_endpoint" {
  description = "Private PostgreSQL endpoint."
  value       = aws_db_instance.main.address
}

output "redis_endpoint" {
  description = "Private Redis endpoint when enabled."
  value       = var.enable_redis ? aws_elasticache_replication_group.main[0].primary_endpoint_address : null
}

output "github_deploy_role_arn" {
  description = "Role ARN for the backend GitHub Actions environment secret."
  value       = var.github_repository == "" ? null : aws_iam_role.github_deploy[0].arn
}
