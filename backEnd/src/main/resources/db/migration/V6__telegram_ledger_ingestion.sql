ALTER TABLE costos ADD COLUMN IF NOT EXISTS owner_id BIGINT REFERENCES usuarios(id);

UPDATE costos c
SET owner_id = (
    SELECT MIN(u.id) FROM usuarios u WHERE u.tenant_id = c.tenant_id
)
WHERE c.owner_id IS NULL AND c.tenant_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_costos_owner ON costos(owner_id);

CREATE TABLE telegram_ledger_ingestions (
  id BIGSERIAL PRIMARY KEY,
  chat_id VARCHAR(64) NOT NULL,
  message_id BIGINT NOT NULL,
  callback_message_id BIGINT,
  tenant_id BIGINT NOT NULL REFERENCES tenants(id),
  status VARCHAR(40) NOT NULL,
  direction VARCHAR(20),
  extraction_json JSONB,
  record_type VARCHAR(20),
  record_id BIGINT,
  failure_reason VARCHAR(500),
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  CONSTRAINT uk_telegram_ingestion_source UNIQUE (chat_id, message_id)
);

CREATE INDEX idx_telegram_ingestion_status_updated
  ON telegram_ledger_ingestions(status, updated_at);
CREATE INDEX idx_telegram_ingestion_tenant
  ON telegram_ledger_ingestions(tenant_id);
