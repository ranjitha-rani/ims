-- Composite B-tree indexes for common customer and admin list filters.
CREATE INDEX ix_claim_customer_status_created
  ON claim (customer_id, status, created_at DESC);

CREATE INDEX ix_claim_status_updated
  ON claim (status, updated_at DESC);

CREATE INDEX ix_policy_customer_status_purchased
  ON policy (customer_id, status, purchased_at DESC);

CREATE INDEX ix_policy_plan_status
  ON policy (plan_id, status);

CREATE INDEX ix_outbox_type_unpublished
  ON outbox_event (event_type, occurred_at)
  WHERE published_at IS NULL;
