CREATE TABLE telegram_connections (
  id BIGSERIAL PRIMARY KEY,
  chat_id VARCHAR(64) NOT NULL UNIQUE,
  tenant_id BIGINT NOT NULL REFERENCES tenants(id),
  default_owner_id BIGINT NOT NULL REFERENCES usuarios(id),
  connected_by_user_id BIGINT REFERENCES usuarios(id),
  chat_type VARCHAR(40),
  chat_title VARCHAR(255),
  enabled BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_telegram_connections_tenant
  ON telegram_connections(tenant_id);

CREATE TABLE telegram_connect_codes (
  id BIGSERIAL PRIMARY KEY,
  code_hash VARCHAR(64) NOT NULL UNIQUE,
  tenant_id BIGINT NOT NULL REFERENCES tenants(id),
  default_owner_id BIGINT NOT NULL REFERENCES usuarios(id),
  created_by_user_id BIGINT REFERENCES usuarios(id),
  expires_at TIMESTAMP NOT NULL,
  consumed_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
