# ADR 0004: Introduce Redis and Kafka only in stages

- Status: Accepted
- Date: 2026-08-08

## Context

Caching and event streaming can improve latency, decoupling, and integration throughput, but both add failure modes, data-consistency decisions, monitoring, and cost. The initial modernization can satisfy core transactions with PostgreSQL and synchronous REST. Adding distributed infrastructure before measured demand would increase risk without proven value.

## Decision

Keep Redis optional in AWS and disabled by default. Introduce it only for measured cache, rate-limit, or short-lived coordination needs; never make it the source of truth. Run a Kafka-compatible Redpanda broker locally so event contracts and the transactional outbox can be developed early, but defer a managed production Kafka service until asynchronous consumers and throughput justify it.

Publish database and application changes through a PostgreSQL transactional outbox. Track backlog age/count and consumer lag before relying on asynchronous delivery. Define retention, retry, dead-letter, idempotency, and schema compatibility before production enablement.

## Consequences

The first production footprint is cheaper and operationally simpler. Local development still exercises future event-driven boundaries. Enabling Redis or Kafka becomes an explicit, observable architecture change instead of a hidden dependency. The tradeoff is that some integrations remain synchronous initially and later rollout requires capacity planning, managed-service selection, and additional incident procedures.
