# IMS AWS infrastructure

The Terraform stack creates a public Application Load Balancer and an API service on ECS Fargate tasks in private subnets. PostgreSQL, optional Redis, task secrets, and runtime logs remain private. A single NAT gateway is deliberately shared across availability zones to control non-production cost; production teams should decide whether the additional resilience of one NAT per AZ justifies the cost.

The separate [OCI Always Free guide](oci/README.md) covers the single ARM VM demonstration path, including the OCI `[DEFAULT]` profile, `us-phoenix-1`, `terraform apply`, capacity failures across availability domains, GitHub configuration, sslip.io HTTPS, Pages integration, backups, and staged Kafka/observability. OCI and AWS state and credentials must remain separate.

## 1. Bootstrap remote state

Run this once per AWS account:

```sh
terraform -chdir=infra/bootstrap init
terraform -chdir=infra/bootstrap apply
terraform -chdir=infra/bootstrap output -raw backend_hcl > /tmp/ims-backend.hcl
```

The state bucket has versioning, encryption, public-access blocking, and `prevent_destroy`. The DynamoDB table supplies state locking. Keep the bootstrap state in an access-controlled location; do not commit it.

## 2. Initialize and apply an environment

The ECR repository must contain the initial image tag before ECS can start successfully. A practical first deployment is:

1. Copy `infra/terraform/terraform.tfvars.example` to an ignored environment-specific `.tfvars` file.
2. Initialize with the generated backend configuration.
3. Target the ECR repository once, push a bootstrap image, then apply the complete stack.

```sh
terraform -chdir=infra/terraform init -backend-config=/tmp/ims-backend.hcl
terraform -chdir=infra/terraform apply -target=aws_ecr_repository.api
aws ecr get-login-password --region us-east-1 |
  docker login --username AWS --password-stdin ACCOUNT.dkr.ecr.us-east-1.amazonaws.com
docker build -t ACCOUNT.dkr.ecr.us-east-1.amazonaws.com/ims-dev-api:bootstrap backend
docker push ACCOUNT.dkr.ecr.us-east-1.amazonaws.com/ims-dev-api:bootstrap
terraform -chdir=infra/terraform apply -var-file=dev.tfvars
```

Use a distinct backend key and variables for each environment. Production should set `api_desired_count = 2`, `db_multi_az = true`, `db_deletion_protection = true`, an ACM `certificate_arn`, `cors_allowed_origins` to the exact GitHub Pages origin, and an alarm email. Terraform rejects production without an ACM certificate. Set `bootstrap_admin_email` only for the initial administrator creation, then clear it after verifying that account.

Kafka remains staged and disabled by default. To connect an external managed broker, set `enable_kafka = true` and `kafka_bootstrap_servers`; this enables both outbox writes and the publisher/consumers. With Kafka disabled, outbox writes are also disabled so the synchronous ECS release cannot build an unbounded unpublished backlog.

## GitHub OIDC

Set `github_repository` to `owner/repository`. Set `create_github_oidc_provider = true` only if the account does not already have GitHub's account-wide OIDC provider. The output `github_deploy_role_arn` is the value for the production environment secret `AWS_DEPLOY_ROLE_ARN`.

Terraform automation needs a separately bootstrapped role with permissions to manage this stack. Store its ARN as `AWS_TERRAFORM_ROLE_ARN`, and store the remote-state names as `TF_STATE_BUCKET` and `TF_LOCK_TABLE`. Protect the `infrastructure-production` GitHub environment with required reviewers. Least-privilege deployment variables are listed in the root README.

## State and secrets

Terraform state contains sensitive generated database and runtime material even though outputs do not reveal it. Restrict state-bucket access and CloudTrail-log access. ECS receives the JDBC URL, database username/password, JWT signing key, and optional bootstrap administrator credentials from Secrets Manager; these values are not placed in GitHub or task-definition environment variables. Rotate database and runtime secrets through controlled maintenance processes.
