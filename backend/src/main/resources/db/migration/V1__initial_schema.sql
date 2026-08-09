CREATE TABLE app_user (
  id UUID PRIMARY KEY, email VARCHAR(320) NOT NULL UNIQUE, password_hash VARCHAR(100) NOT NULL,
  display_name VARCHAR(120) NOT NULL, role VARCHAR(20) NOT NULL CHECK (role IN ('CUSTOMER','ADMIN')),
  created_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE plan (
  id UUID PRIMARY KEY, code VARCHAR(50) NOT NULL UNIQUE, name VARCHAR(120) NOT NULL,
  premium NUMERIC(12,2) NOT NULL CHECK (premium >= 0), active BOOLEAN NOT NULL
);
CREATE TABLE policy (
  id UUID PRIMARY KEY, policy_number VARCHAR(40) NOT NULL UNIQUE, customer_id UUID NOT NULL REFERENCES app_user(id),
  plan_id UUID NOT NULL REFERENCES plan(id), status VARCHAR(20) NOT NULL, purchased_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE payment (
  id UUID PRIMARY KEY, policy_id UUID NOT NULL REFERENCES policy(id), amount NUMERIC(12,2) NOT NULL,
  provider_reference VARCHAR(100) NOT NULL UNIQUE, status VARCHAR(20) NOT NULL, paid_at TIMESTAMPTZ
);
CREATE TABLE claim (
  id UUID PRIMARY KEY, policy_id UUID NOT NULL REFERENCES policy(id), customer_id UUID NOT NULL REFERENCES app_user(id),
  description VARCHAR(2000) NOT NULL, amount NUMERIC(12,2) NOT NULL CHECK (amount > 0),
  status VARCHAR(30) NOT NULL CHECK (status IN ('SUBMITTED','UNDER_REVIEW','APPROVED','REJECTED','PAID')),
  created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE outbox_event (
  id UUID PRIMARY KEY, aggregate_type VARCHAR(60) NOT NULL, aggregate_id UUID NOT NULL,
  event_type VARCHAR(80) NOT NULL, payload TEXT NOT NULL, occurred_at TIMESTAMPTZ NOT NULL, published_at TIMESTAMPTZ
);
CREATE INDEX ix_outbox_unpublished ON outbox_event (occurred_at) WHERE published_at IS NULL;
CREATE TABLE consumed_event (
  consumer_name VARCHAR(80) NOT NULL, event_id UUID NOT NULL, consumed_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (consumer_name, event_id)
);
CREATE TABLE audit_event (
  id UUID PRIMARY KEY, event_id UUID NOT NULL UNIQUE, event_type VARCHAR(80) NOT NULL,
  payload TEXT NOT NULL, recorded_at TIMESTAMPTZ NOT NULL
);
