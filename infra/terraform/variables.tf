variable "project_name" {
  description = "Short name used for AWS resources."
  type        = string
  default     = "ims"
}

variable "environment" {
  description = "Deployment environment."
  type        = string
  default     = "dev"

  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "environment must be dev, staging, or prod."
  }
}

variable "aws_region" {
  description = "AWS region."
  type        = string
  default     = "us-east-1"
}

variable "vpc_cidr" {
  description = "VPC IPv4 CIDR."
  type        = string
  default     = "10.20.0.0/16"
}

variable "availability_zone_count" {
  description = "Number of AZs. RDS requires at least two."
  type        = number
  default     = 2
}

variable "api_image_tag" {
  description = "ECR image tag deployed to ECS."
  type        = string
  default     = "latest"
}

variable "api_container_port" {
  description = "API container port."
  type        = number
  default     = 8080
}

variable "api_health_check_path" {
  description = "Unauthenticated API health endpoint."
  type        = string
  default     = "/actuator/health"
}

variable "api_desired_count" {
  description = "Steady-state ECS task count."
  type        = number
  default     = 1
}

variable "api_cpu" {
  description = "Fargate task CPU units."
  type        = number
  default     = 256
}

variable "api_memory" {
  description = "Fargate task memory MiB."
  type        = number
  default     = 512
}

variable "db_name" {
  description = "Initial PostgreSQL database."
  type        = string
  default     = "ims"
}

variable "db_username" {
  description = "PostgreSQL administrator username."
  type        = string
  default     = "ims_admin"
}

variable "db_instance_class" {
  description = "RDS instance class."
  type        = string
  default     = "db.t4g.micro"
}

variable "db_allocated_storage" {
  description = "Initial RDS gp3 storage GiB."
  type        = number
  default     = 20
}

variable "db_multi_az" {
  description = "Enable Multi-AZ RDS. Recommended for production."
  type        = bool
  default     = false
}

variable "db_deletion_protection" {
  description = "Protect RDS from deletion."
  type        = bool
  default     = false
}

variable "enable_redis" {
  description = "Create an ElastiCache Redis-compatible cache."
  type        = bool
  default     = false
}

variable "enable_kafka" {
  description = "Enable the transactional outbox publisher and Kafka consumers using an externally managed broker."
  type        = bool
  default     = false
}

variable "kafka_bootstrap_servers" {
  description = "Comma-separated bootstrap servers for the external Kafka provider when enable_kafka is true."
  type        = string
  default     = ""
}

variable "redis_node_type" {
  description = "ElastiCache node type."
  type        = string
  default     = "cache.t4g.micro"
}

variable "log_retention_days" {
  description = "CloudWatch log retention."
  type        = number
  default     = 14
}

variable "alarm_email" {
  description = "Optional email subscription for CloudWatch alarms."
  type        = string
  default     = ""
}

variable "certificate_arn" {
  description = "Optional ACM certificate ARN; enables HTTPS and redirects HTTP."
  type        = string
  default     = ""
}

variable "cors_allowed_origins" {
  description = "Comma-separated browser origins allowed by the API, including the GitHub Pages origin."
  type        = string
  default     = "http://localhost:5173"
}

variable "bootstrap_admin_email" {
  description = "Optional initial administrator email. Empty disables administrator bootstrap."
  type        = string
  default     = ""
  sensitive   = true
}

variable "bootstrap_admin_name" {
  description = "Display name for the optional initial administrator."
  type        = string
  default     = "IMS Administrator"
}

variable "allowed_ingress_cidrs" {
  description = "CIDRs allowed to reach the public ALB."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "additional_environment" {
  description = "Non-secret environment variables supplied to the API."
  type        = map(string)
  default     = {}
}

variable "tags" {
  description = "Additional resource tags."
  type        = map(string)
  default     = {}
}
