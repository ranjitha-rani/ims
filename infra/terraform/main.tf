data "aws_availability_zones" "available" {
  state = "available"
}

data "aws_caller_identity" "current" {}

locals {
  name = "${var.project_name}-${var.environment}"
  azs  = slice(data.aws_availability_zones.available.names, 0, var.availability_zone_count)
  common_tags = merge({
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "terraform"
  }, var.tags)
}

resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = { Name = local.name }
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id
  tags   = { Name = local.name }
}

resource "aws_subnet" "public" {
  for_each = { for index, az in local.azs : az => index }

  vpc_id                  = aws_vpc.main.id
  availability_zone       = each.key
  cidr_block              = cidrsubnet(var.vpc_cidr, 8, each.value)
  map_public_ip_on_launch = true
  tags                    = { Name = "${local.name}-public-${each.key}" }
}

resource "aws_subnet" "private" {
  for_each = { for index, az in local.azs : az => index }

  vpc_id            = aws_vpc.main.id
  availability_zone = each.key
  cidr_block        = cidrsubnet(var.vpc_cidr, 8, each.value + 10)
  tags              = { Name = "${local.name}-private-${each.key}" }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = { Name = "${local.name}-public" }
}

resource "aws_route_table_association" "public" {
  for_each = aws_subnet.public

  subnet_id      = each.value.id
  route_table_id = aws_route_table.public.id
}

# One NAT gateway minimizes non-production cost. Production can evolve to one per AZ.
resource "aws_eip" "nat" {
  domain = "vpc"
  tags   = { Name = "${local.name}-nat" }

  depends_on = [aws_internet_gateway.main]
}

resource "aws_nat_gateway" "main" {
  allocation_id = aws_eip.nat.id
  subnet_id     = values(aws_subnet.public)[0].id
  tags          = { Name = local.name }

  depends_on = [aws_route_table_association.public]
}

resource "aws_route_table" "private" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.main.id
  }

  tags = { Name = "${local.name}-private" }
}

resource "aws_route_table_association" "private" {
  for_each = aws_subnet.private

  subnet_id      = each.value.id
  route_table_id = aws_route_table.private.id
}

resource "aws_security_group" "alb" {
  name_prefix = "${local.name}-alb-"
  description = "Public traffic to the IMS ALB"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "HTTP"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = var.allowed_ingress_cidrs
  }

  dynamic "ingress" {
    for_each = var.certificate_arn == "" ? [] : [1]
    content {
      description = "HTTPS"
      from_port   = 443
      to_port     = 443
      protocol    = "tcp"
      cidr_blocks = var.allowed_ingress_cidrs
    }
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_security_group" "api" {
  name_prefix = "${local.name}-api-"
  description = "ALB traffic to private ECS tasks"
  vpc_id      = aws_vpc.main.id

  ingress {
    from_port       = var.api_container_port
    to_port         = var.api_container_port
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_security_group" "database" {
  name_prefix = "${local.name}-db-"
  description = "PostgreSQL traffic from ECS only"
  vpc_id      = aws_vpc.main.id

  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.api.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_security_group" "redis" {
  count = var.enable_redis ? 1 : 0

  name_prefix = "${local.name}-redis-"
  description = "Redis traffic from ECS only"
  vpc_id      = aws_vpc.main.id

  ingress {
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [aws_security_group.api.id]
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_ecr_repository" "api" {
  name                 = "${local.name}-api"
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }
}

resource "aws_ecr_lifecycle_policy" "api" {
  repository = aws_ecr_repository.api.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Retain the newest 20 images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 20
      }
      action = { type = "expire" }
    }]
  })
}

resource "random_password" "database" {
  length           = 32
  special          = true
  override_special = "!#$%&*()-_=+[]{}<>:?"
}

resource "random_password" "jwt" {
  length  = 64
  special = false
}

resource "random_password" "bootstrap_admin" {
  length           = 32
  special          = true
  override_special = "!#$%&*()-_=+[]{}<>:?"
}

resource "aws_db_subnet_group" "main" {
  name       = local.name
  subnet_ids = values(aws_subnet.private)[*].id
  tags       = { Name = local.name }
}

resource "aws_db_instance" "main" {
  identifier = local.name

  engine                       = "postgres"
  engine_version               = "16"
  instance_class               = var.db_instance_class
  allocated_storage            = var.db_allocated_storage
  max_allocated_storage        = max(var.db_allocated_storage, 100)
  storage_type                 = "gp3"
  storage_encrypted            = true
  db_name                      = var.db_name
  username                     = var.db_username
  password                     = random_password.database.result
  port                         = 5432
  db_subnet_group_name         = aws_db_subnet_group.main.name
  vpc_security_group_ids       = [aws_security_group.database.id]
  publicly_accessible          = false
  multi_az                     = var.db_multi_az
  backup_retention_period      = var.environment == "prod" ? 7 : 1
  deletion_protection          = var.db_deletion_protection
  skip_final_snapshot          = var.environment != "prod"
  final_snapshot_identifier    = var.environment == "prod" ? "${local.name}-final" : null
  auto_minor_version_upgrade   = true
  apply_immediately            = var.environment != "prod"
  performance_insights_enabled = false

  tags = { Name = local.name }
}

resource "aws_secretsmanager_secret" "database" {
  name                    = "${local.name}/database"
  recovery_window_in_days = var.environment == "prod" ? 30 : 0
}

resource "aws_secretsmanager_secret_version" "database" {
  secret_id = aws_secretsmanager_secret.database.id
  secret_string = jsonencode({
    username          = var.db_username
    password          = random_password.database.result
    host              = aws_db_instance.main.address
    port              = aws_db_instance.main.port
    dbname            = var.db_name
    DATABASE_URL      = "jdbc:postgresql://${aws_db_instance.main.address}:${aws_db_instance.main.port}/${var.db_name}"
    DATABASE_USER     = var.db_username
    DATABASE_PASSWORD = random_password.database.result
  })
}

resource "aws_secretsmanager_secret" "runtime" {
  name                    = "${local.name}/runtime"
  recovery_window_in_days = var.environment == "prod" ? 30 : 0
}

resource "aws_secretsmanager_secret_version" "runtime" {
  secret_id = aws_secretsmanager_secret.runtime.id
  secret_string = jsonencode({
    JWT_SECRET                   = random_password.jwt.result
    IMS_BOOTSTRAP_ADMIN_EMAIL    = var.bootstrap_admin_email
    IMS_BOOTSTRAP_ADMIN_PASSWORD = random_password.bootstrap_admin.result
  })
}

resource "aws_elasticache_subnet_group" "main" {
  count = var.enable_redis ? 1 : 0

  name       = local.name
  subnet_ids = values(aws_subnet.private)[*].id
}

resource "aws_elasticache_replication_group" "main" {
  count = var.enable_redis ? 1 : 0

  replication_group_id       = local.name
  description                = "IMS staged Redis cache"
  engine                     = "redis"
  node_type                  = var.redis_node_type
  port                       = 6379
  num_cache_clusters         = 1
  automatic_failover_enabled = false
  transit_encryption_enabled = true
  at_rest_encryption_enabled = true
  subnet_group_name          = aws_elasticache_subnet_group.main[0].name
  security_group_ids         = [aws_security_group.redis[0].id]
  snapshot_retention_limit   = var.environment == "prod" ? 3 : 0
}

resource "aws_cloudwatch_log_group" "api" {
  name              = "/ecs/${local.name}/api"
  retention_in_days = var.log_retention_days
}

resource "aws_ecs_cluster" "main" {
  name = local.name

  setting {
    name  = "containerInsights"
    value = var.environment == "prod" ? "enabled" : "disabled"
  }
}

resource "aws_iam_role" "ecs_execution" {
  name = "${local.name}-ecs-execution"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "ecs_execution" {
  role       = aws_iam_role.ecs_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role_policy" "ecs_secrets" {
  name = "read-runtime-secrets"
  role = aws_iam_role.ecs_execution.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = ["secretsmanager:GetSecretValue"]
      Resource = [
        aws_secretsmanager_secret.database.arn,
        aws_secretsmanager_secret.runtime.arn
      ]
    }]
  })
}

resource "aws_iam_role" "api_task" {
  name = "${local.name}-api-task"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_lb" "api" {
  name                       = substr("${local.name}-api", 0, 32)
  internal                   = false
  load_balancer_type         = "application"
  security_groups            = [aws_security_group.alb.id]
  subnets                    = values(aws_subnet.public)[*].id
  drop_invalid_header_fields = true

  lifecycle {
    precondition {
      condition     = var.environment != "prod" || var.certificate_arn != ""
      error_message = "Production deployments require certificate_arn so credentials and tokens are never sent over HTTP."
    }
  }
}

resource "aws_lb_target_group" "api" {
  name        = substr("${local.name}-api", 0, 32)
  port        = var.api_container_port
  protocol    = "HTTP"
  target_type = "ip"
  vpc_id      = aws_vpc.main.id

  health_check {
    enabled             = true
    path                = var.api_health_check_path
    healthy_threshold   = 2
    unhealthy_threshold = 3
    timeout             = 5
    interval            = 30
    matcher             = "200-399"
  }

  deregistration_delay = 30
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.api.arn
  port              = 80
  protocol          = "HTTP"

  dynamic "default_action" {
    for_each = var.certificate_arn == "" ? [1] : []
    content {
      type             = "forward"
      target_group_arn = aws_lb_target_group.api.arn
    }
  }

  dynamic "default_action" {
    for_each = var.certificate_arn == "" ? [] : [1]
    content {
      type = "redirect"
      redirect {
        port        = "443"
        protocol    = "HTTPS"
        status_code = "HTTP_301"
      }
    }
  }
}

resource "aws_lb_listener" "https" {
  count = var.certificate_arn == "" ? 0 : 1

  load_balancer_arn = aws_lb.api.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = var.certificate_arn

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.api.arn
  }
}

resource "aws_lb_listener_rule" "block_public_metrics_http" {
  listener_arn = aws_lb_listener.http.arn
  priority     = 10

  action {
    type = "fixed-response"
    fixed_response {
      content_type = "application/json"
      message_body = "{\"error\":\"not_found\"}"
      status_code  = "404"
    }
  }

  condition {
    path_pattern {
      values = ["/actuator/prometheus", "/actuator/metrics", "/actuator/metrics/*"]
    }
  }
}

resource "aws_lb_listener_rule" "block_public_metrics_https" {
  count = var.certificate_arn == "" ? 0 : 1

  listener_arn = aws_lb_listener.https[0].arn
  priority     = 10

  action {
    type = "fixed-response"
    fixed_response {
      content_type = "application/json"
      message_body = "{\"error\":\"not_found\"}"
      status_code  = "404"
    }
  }

  condition {
    path_pattern {
      values = ["/actuator/prometheus", "/actuator/metrics", "/actuator/metrics/*"]
    }
  }
}

resource "aws_ecs_task_definition" "api" {
  family                   = "${local.name}-api"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.api_cpu
  memory                   = var.api_memory
  execution_role_arn       = aws_iam_role.ecs_execution.arn
  task_role_arn            = aws_iam_role.api_task.arn

  container_definitions = jsonencode([{
    name      = "api"
    image     = "${aws_ecr_repository.api.repository_url}:${var.api_image_tag}"
    essential = true
    portMappings = [{
      containerPort = var.api_container_port
      hostPort      = var.api_container_port
      protocol      = "tcp"
    }]
    environment = concat(
      [
        { name = "PORT", value = tostring(var.api_container_port) },
        { name = "ENVIRONMENT", value = var.environment },
        { name = "KAFKA_ENABLED", value = tostring(var.enable_kafka) },
        { name = "OUTBOX_ENABLED", value = tostring(var.enable_kafka) },
        { name = "REDIS_ENABLED", value = tostring(var.enable_redis) },
        { name = "REDIS_SSL", value = tostring(var.enable_redis) },
        { name = "CORS_ALLOWED_ORIGINS", value = var.cors_allowed_origins },
        { name = "IMS_BOOTSTRAP_ADMIN_NAME", value = var.bootstrap_admin_name }
      ],
      var.enable_redis ? [
        { name = "REDIS_HOST", value = aws_elasticache_replication_group.main[0].primary_endpoint_address },
        { name = "REDIS_PORT", value = "6379" }
      ] : [],
      var.enable_kafka ? [
        { name = "KAFKA_BOOTSTRAP_SERVERS", value = var.kafka_bootstrap_servers }
      ] : [],
      [for key, value in var.additional_environment : { name = key, value = value }]
    )
    secrets = [
      {
        name      = "DATABASE_URL"
        valueFrom = "${aws_secretsmanager_secret.database.arn}:DATABASE_URL::"
      },
      {
        name      = "DATABASE_USER"
        valueFrom = "${aws_secretsmanager_secret.database.arn}:DATABASE_USER::"
      },
      {
        name      = "DATABASE_PASSWORD"
        valueFrom = "${aws_secretsmanager_secret.database.arn}:DATABASE_PASSWORD::"
      },
      {
        name      = "JWT_SECRET"
        valueFrom = "${aws_secretsmanager_secret.runtime.arn}:JWT_SECRET::"
      },
      {
        name      = "IMS_BOOTSTRAP_ADMIN_EMAIL"
        valueFrom = "${aws_secretsmanager_secret.runtime.arn}:IMS_BOOTSTRAP_ADMIN_EMAIL::"
      },
      {
        name      = "IMS_BOOTSTRAP_ADMIN_PASSWORD"
        valueFrom = "${aws_secretsmanager_secret.runtime.arn}:IMS_BOOTSTRAP_ADMIN_PASSWORD::"
      }
    ]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.api.name
        awslogs-region        = var.aws_region
        awslogs-stream-prefix = "api"
      }
    }
  }])

  depends_on = [
    aws_secretsmanager_secret_version.database,
    aws_secretsmanager_secret_version.runtime
  ]

  lifecycle {
    precondition {
      condition     = !var.enable_kafka || var.kafka_bootstrap_servers != ""
      error_message = "kafka_bootstrap_servers is required when enable_kafka is true."
    }
  }
}

resource "aws_ecs_service" "api" {
  name                               = "api"
  cluster                            = aws_ecs_cluster.main.id
  task_definition                    = aws_ecs_task_definition.api.arn
  desired_count                      = var.api_desired_count
  launch_type                        = "FARGATE"
  health_check_grace_period_seconds  = 60
  deployment_minimum_healthy_percent = var.api_desired_count > 1 ? 50 : 100
  deployment_maximum_percent         = 200
  enable_execute_command             = true

  network_configuration {
    subnets          = values(aws_subnet.private)[*].id
    security_groups  = [aws_security_group.api.id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.api.arn
    container_name   = "api"
    container_port   = var.api_container_port
  }

  lifecycle {
    ignore_changes = [task_definition]
  }

  depends_on = [aws_lb_listener.http, aws_lb_listener.https]
}

resource "aws_appautoscaling_target" "api" {
  max_capacity       = var.environment == "prod" ? 6 : 2
  min_capacity       = var.api_desired_count
  resource_id        = "service/${aws_ecs_cluster.main.name}/${aws_ecs_service.api.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"
}

resource "aws_appautoscaling_policy" "api_cpu" {
  name               = "${local.name}-cpu"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.api.resource_id
  scalable_dimension = aws_appautoscaling_target.api.scalable_dimension
  service_namespace  = aws_appautoscaling_target.api.service_namespace

  target_tracking_scaling_policy_configuration {
    target_value = 65
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
  }
}

resource "aws_sns_topic" "alarms" {
  name = "${local.name}-alarms"
}

resource "aws_sns_topic_subscription" "email" {
  count = var.alarm_email == "" ? 0 : 1

  topic_arn = aws_sns_topic.alarms.arn
  protocol  = "email"
  endpoint  = var.alarm_email
}

resource "aws_cloudwatch_metric_alarm" "api_5xx" {
  alarm_name          = "${local.name}-api-5xx"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "HTTPCode_Target_5XX_Count"
  namespace           = "AWS/ApplicationELB"
  period              = 300
  statistic           = "Sum"
  threshold           = 5
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alarms.arn]
  dimensions = {
    LoadBalancer = aws_lb.api.arn_suffix
    TargetGroup  = aws_lb_target_group.api.arn_suffix
  }
}

resource "aws_cloudwatch_metric_alarm" "api_unhealthy" {
  alarm_name          = "${local.name}-api-unhealthy"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "UnHealthyHostCount"
  namespace           = "AWS/ApplicationELB"
  period              = 60
  statistic           = "Maximum"
  threshold           = 0
  treat_missing_data  = "breaching"
  alarm_actions       = [aws_sns_topic.alarms.arn]
  dimensions = {
    LoadBalancer = aws_lb.api.arn_suffix
    TargetGroup  = aws_lb_target_group.api.arn_suffix
  }
}

resource "aws_cloudwatch_metric_alarm" "database_storage" {
  alarm_name          = "${local.name}-db-storage-low"
  comparison_operator = "LessThanThreshold"
  evaluation_periods  = 1
  metric_name         = "FreeStorageSpace"
  namespace           = "AWS/RDS"
  period              = 300
  statistic           = "Average"
  threshold           = 5368709120
  treat_missing_data  = "missing"
  alarm_actions       = [aws_sns_topic.alarms.arn]
  dimensions = {
    DBInstanceIdentifier = aws_db_instance.main.identifier
  }
}
