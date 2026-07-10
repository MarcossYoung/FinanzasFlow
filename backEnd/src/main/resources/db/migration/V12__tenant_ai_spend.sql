CREATE TABLE tenant_ai_spend (
  id BIGSERIAL PRIMARY KEY,
  tenant_id BIGINT NOT NULL REFERENCES tenants(id),
  period_yyyymm VARCHAR(6) NOT NULL,
  spend_cents NUMERIC(12,4) NOT NULL DEFAULT 0,
  updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_tenant_ai_spend_tenant_period UNIQUE (tenant_id, period_yyyymm)
);
