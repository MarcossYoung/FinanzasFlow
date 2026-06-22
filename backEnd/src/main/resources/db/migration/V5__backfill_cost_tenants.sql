UPDATE costos
SET tenant_id = (SELECT id FROM tenants ORDER BY id LIMIT 1)
WHERE tenant_id IS NULL
  AND EXISTS (SELECT 1 FROM tenants);

CREATE INDEX IF NOT EXISTS idx_costos_tenant ON costos(tenant_id);
