ALTER TABLE claim ADD COLUMN admin_notes VARCHAR(2000);

CREATE TABLE notification_log (
  id UUID PRIMARY KEY,
  event_id UUID NOT NULL UNIQUE,
  event_type VARCHAR(80) NOT NULL,
  recipient_user_id UUID,
  channel VARCHAR(40) NOT NULL,
  subject VARCHAR(200) NOT NULL,
  body TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL
);
