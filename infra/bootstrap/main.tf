terraform {
  required_version = ">= 1.7.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project   = var.project_name
      ManagedBy = "terraform-bootstrap"
    }
  }
}

variable "project_name" {
  type    = string
  default = "ims"
}

variable "aws_region" {
  type    = string
  default = "us-east-1"
}

variable "state_bucket_name" {
  description = "Globally unique S3 bucket name. Leave empty to generate a suffix."
  type        = string
  default     = ""
}

resource "random_id" "suffix" {
  byte_length = 4
}

locals {
  bucket_name = var.state_bucket_name != "" ? var.state_bucket_name : "${var.project_name}-terraform-state-${random_id.suffix.hex}"
}

resource "aws_s3_bucket" "state" {
  bucket = local.bucket_name

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_s3_bucket_versioning" "state" {
  bucket = aws_s3_bucket.state.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "state" {
  bucket = aws_s3_bucket.state.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "state" {
  bucket                  = aws_s3_bucket.state.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_dynamodb_table" "locks" {
  name         = "${var.project_name}-terraform-locks"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "LockID"

  attribute {
    name = "LockID"
    type = "S"
  }

  point_in_time_recovery {
    enabled = true
  }
}

output "state_bucket" {
  value = aws_s3_bucket.state.id
}

output "lock_table" {
  value = aws_dynamodb_table.locks.name
}

output "backend_hcl" {
  value = <<-EOT
    bucket         = "${aws_s3_bucket.state.id}"
    key            = "environments/dev/terraform.tfstate"
    region         = "${var.aws_region}"
    dynamodb_table = "${aws_dynamodb_table.locks.name}"
    encrypt        = true
  EOT
}
