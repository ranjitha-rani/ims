# ADR 0002: Use a versioned REST API

- Status: Accepted
- Date: 2026-08-08

## Context

The modern frontend needs a stable boundary around policy, claim, customer, and payment workflows while the legacy implementation is replaced incrementally. The domain is resource-oriented and does not initially require the schema flexibility or subscription model of GraphQL. Browser and operational tooling have broad HTTP/JSON support.

## Decision

Expose versioned REST resources over HTTPS. Use standard methods and status codes, JSON request/response bodies, explicit pagination and idempotency where writes can be retried, and an OpenAPI contract. Keep health and Prometheus metrics endpoints separate from business authorization while limiting the information they reveal.

## Consequences

REST is easy to cache, observe, test, and consume from GitHub Pages. Versioning and additive changes allow staged migration. Some workflows may require multiple requests or purpose-built aggregate endpoints. API compatibility must be governed, and breaking changes require a new version or a coordinated deprecation.
