ALTER TABLE tenants ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE tenant_activity (
  id BIGSERIAL PRIMARY KEY,
  tenant_id BIGINT NOT NULL REFERENCES tenants(id),
  action_type VARCHAR(100) NOT NULL,
  actor_user_id BIGINT REFERENCES usuarios(id),
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tenant_activity_tenant_created_at ON tenant_activity(tenant_id, created_at);
