# ADR 0005: OCI Always Free VM delivery

- Status: Accepted
- Date: 2026-08-08

## Context

The AWS architecture is production-oriented but its load balancer, NAT gateway, database, and runtime have ongoing cost. A demonstration environment needs an independently deployable path that can remain within OCI's current Always Free allowance. The application image must also support OCI Ampere A1's ARM64 architecture.

## Decision

Use one eligible Ampere A1 VM in `us-phoenix-1` for the low-cost demonstration environment. Terraform provisions infrastructure, while a manually triggered GitHub Actions workflow connects with SSH, copies versioned runtime assets, builds the API on the ARM host, starts Docker Compose, and checks the public HTTPS health endpoint.

Use an automatically issued TLS certificate and an sslip.io hostname until a managed domain is justified. Keep PostgreSQL on a persistent Docker volume. Start without Kafka/Redpanda and the full observability suite; add them only after workload and resource measurements justify their memory and storage cost. The existing AWS deployment remains available only by manual dispatch.

## Consequences

- Native ARM builds avoid cross-architecture image failures and a paid container registry.
- A single VM has an availability, scaling, and maintenance ceiling and is not equivalent to the AWS production design.
- Always Free capacity is not guaranteed. Provisioning may need retries across availability and fault domains.
- Backups must leave the VM and restores must be tested.
- Operators must verify current OCI eligibility, estimates, and budgets; this decision does not guarantee a `$0` bill.
- SSH host keys and deployment environments require operational controls, and application secrets remain on the VM rather than in deployment archives.
