# InsureFlow API benchmarks

Measured against the live OCI demo at `https://ims.144.24.50.163.sslip.io` using [hey](https://github.com/rakyll/hey).

| Field | Value |
| --- | --- |
| Date (UTC) | 2026-08-09 |
| Tool | hey 0.1.5 |
| Duration / concurrency | 30s / 25 |
| Code SHA (benchmarked) | `4dafc35` (Add composite indexes and accurate auth wording) |
| Deploy | Manual SSH deploy of `2b2eaa2` to `144.24.50.163` (GitHub Actions run [31293803693](https://github.com/ranjitha-rani/ims/actions/runs/31293803693) never started — no self-hosted runners) |
| Auth | JWT as `priya.sharma@example.com` for `/api/claims` and `/api/policies` |
| Indexes (V3) | **Applied** — Flyway version `3` (`composite indexes`) succeeded; confirmed `ix_claim_customer_status_created`, `ix_claim_status_updated`, `ix_policy_customer_status_purchased`, `ix_policy_plan_status`, `ix_outbox_type_unpublished` in `pg_indexes` |

## Results

All responses were HTTP 200. Numbers below were captured before the SSH redeploy that applied Flyway V3; latency may improve slightly with the new indexes under load.

| Endpoint | Req/sec | Avg | p50 | p95 | p99 |
| --- | ---: | ---: | ---: | ---: | ---: |
| `GET /health` | 180.35 | 138.2 ms | 127.6 ms | 222.4 ms | 287.7 ms |
| `GET /api/plans` | 187.95 | 132.7 ms | 117.4 ms | 218.1 ms | 276.2 ms |
| `GET /api/claims` (JWT) | 176.70 | 141.2 ms | 122.9 ms | 230.3 ms | 284.4 ms |
| `GET /api/policies` (JWT) | 164.66 | 151.3 ms | 139.9 ms | 236.4 ms | 321.0 ms |

## How to reproduce

```bash
# Public
hey -z 30s -c 25 https://ims.144.24.50.163.sslip.io/health
hey -z 30s -c 25 https://ims.144.24.50.163.sslip.io/api/plans

# Authenticated (pass Authorization header; hey 0.1.x does not support -H @file)
TOKEN=$(curl -fsS -X POST https://ims.144.24.50.163.sslip.io/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"priya.sharma@example.com","password":"'"$CUSTOMER_PASSWORD"'"}' \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["accessToken"])')

hey -z 30s -c 25 -H "Authorization: Bearer $TOKEN" \
  https://ims.144.24.50.163.sslip.io/api/claims
hey -z 30s -c 25 -H "Authorization: Bearer $TOKEN" \
  https://ims.144.24.50.163.sslip.io/api/policies
```

Or use `scripts/load-test.sh` after setting `CUSTOMER_PASSWORD` (adjust header passing for your hey version).

## Notes

- Initial hey run used the live API while Actions deploy was stuck pending (no registered self-hosted runners).
- A later manual SSH deploy of `2b2eaa2` rebuilt/restarted the API via `compose.oci.yaml` + eventing/observability profiles and applied Flyway V3 on the live Postgres.
