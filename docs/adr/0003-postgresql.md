# ADR 0003: Use PostgreSQL as the system of record

- Status: Accepted
- Date: 2026-08-08

## Context

Insurance records have strong relationships and transactional invariants. Policy issuance, claims, and payments need constraints, atomic updates, auditable changes, and reliable backup/restore. The access patterns are not yet stable enough to justify distributing core records across specialized databases.

## Decision

Use PostgreSQL on encrypted Amazon RDS as the authoritative store. Keep it private, expose it only to the API security group, manage schema migrations with the backend release process, and use an outbox table for events that must be published reliably.

## Consequences

PostgreSQL provides mature transactions, indexing, JSON support, and operational tooling. RDS reduces database administration while retaining SQL portability. Vertical limits and connection pressure require pooling and monitoring. Production pays for Multi-AZ resilience; development defaults to a single small instance. Terraform state and Secrets Manager handling must remain tightly controlled because they participate in credential creation.
