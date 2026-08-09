# IMS backend

Java 21 / Spring Boot API for identity, plans, policies, payments, and claims. PostgreSQL is the system of record; Flyway owns the schema. Domain events use a transactional outbox and Kafka. Redis-backed refresh-token revocation and login throttling degrade to local stateless behavior when Redis is unavailable.

## Run

Prerequisites: Java 21 and PostgreSQL. The checked-in Maven wrapper downloads Maven automatically. Kafka and Redis are optional for local API work.

Environment variables:

- `DATABASE_URL`: full PostgreSQL JDBC URL, for example `jdbc:postgresql://localhost:5432/ims`
- `DATABASE_USER`: PostgreSQL username
- `DATABASE_PASSWORD`: PostgreSQL password
- `REDIS_HOST`: Redis hostname (defaults to `localhost`)
- `REDIS_PORT`: Redis port (defaults to `6379`)
- `CORS_ALLOWED_ORIGINS`: comma-separated browser origins, for example `http://localhost:5173`

```bash
export DATABASE_URL=jdbc:postgresql://localhost:5432/ims
export DATABASE_USER=ims
export DATABASE_PASSWORD=ims
export REDIS_HOST=localhost
export REDIS_PORT=6379
export CORS_ALLOWED_ORIGINS=http://localhost:5173
export JWT_SECRET='replace-with-a-random-secret-of-at-least-32-bytes'
export IMS_BOOTSTRAP_ADMIN_EMAIL=admin@example.com
export IMS_BOOTSTRAP_ADMIN_PASSWORD='replace-with-a-strong-password'
export KAFKA_ENABLED=false       # set true when Kafka is available
export OUTBOX_ENABLED=false      # normally enabled together with Kafka
export REDIS_ENABLED=false      # Redis operations already fail open
./mvnw spring-boot:run
```

With Kafka enabled, configure `KAFKA_BOOTSTRAP_SERVERS`. Events are published to `ims.domain-events`; exhausted retries go to `ims.domain-events.DLT`. The three independent consumer groups provide claim validation, notification placeholder logging, and durable auditing.

Policy enrollment records a server-generated internal payment reference; it does not charge a real payment method. Integrate a payment provider and activate policies only from verified callbacks before using the platform for real billing.

## API summary

- `POST /api/auth/register|login|refresh|logout`
- `/api/users`, `/api/users/customers`, `/api/users/admins`, `/api/users/me`
- `/api/plans`
- `/api/policies` and `/api/policies/{id}/payments`
- `/api/claims` and `PATCH /api/claims/{id}/status`
- `/actuator/health` and `/actuator/prometheus`

Use `Authorization: Bearer <accessToken>`. Customer resources enforce ownership; administration and claim transitions require `ADMIN`. Claim transitions are strictly `SUBMITTED -> UNDER_REVIEW -> APPROVED -> PAID`, or `UNDER_REVIEW -> REJECTED`.

## Verify

```bash
./mvnw test
docker build -t ims-backend .
```

All errors produced by controllers use RFC 9457 problem details. Send `X-Correlation-ID` to preserve a caller ID in response headers and structured log context.
