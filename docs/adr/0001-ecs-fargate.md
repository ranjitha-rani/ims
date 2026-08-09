# ADR 0001: Run the API on ECS Fargate

- Status: Accepted
- Date: 2026-08-08

## Context

IMS needs a managed container runtime with private networking, rolling deployment, autoscaling, AWS IAM integration, and low operational burden. Kubernetes would add cluster administration before the product has demonstrated that need. Lambda would constrain request/runtime behavior and complicate a conventional long-running API.

## Decision

Run the API as an ECS service on Fargate behind an Application Load Balancer. Place tasks in private subnets, use `awsvpc` security groups, pull immutable images from ECR, inject secrets through Secrets Manager, and send logs to CloudWatch.

## Consequences

The team avoids host and control-plane management and retains normal container semantics. Fargate and ALB have a price premium over a single VM, but reduce patching and deployment risk. ECS-specific deployment configuration creates some AWS coupling. If workload scale or platform requirements later justify Kubernetes, OCI images and the stateless API boundary preserve a migration path.
