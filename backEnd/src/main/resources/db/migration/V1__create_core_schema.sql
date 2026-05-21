CREATE TABLE tenants (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  slug VARCHAR(100) UNIQUE NOT NULL,
  email VARCHAR(255),
  phone VARCHAR(50),
  created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE usuarios (
  id BIGSERIAL PRIMARY KEY,
  username VARCHAR(255) NOT NULL UNIQUE,
  password VARCHAR(255) NOT NULL,
  app_user_role VARCHAR(50) NOT NULL,
  phone_number VARCHAR(50),
  tenant_id BIGINT REFERENCES tenants(id)
);

CREATE TABLE customers (
  id BIGSERIAL PRIMARY KEY,
  tenant_id BIGINT NOT NULL REFERENCES tenants(id),
  name VARCHAR(255) NOT NULL,
  cuit_dni VARCHAR(30),
  email VARCHAR(255),
  phone VARCHAR(50),
  notes TEXT,
  payment_score INTEGER DEFAULT 100,
  created_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_customers_tenant ON customers(tenant_id);

CREATE TABLE invoices (
  id BIGSERIAL PRIMARY KEY,
  tenant_id BIGINT REFERENCES tenants(id),
  customer_id BIGINT REFERENCES customers(id),
  ownerid BIGINT REFERENCES usuarios(id),
  titulo VARCHAR(255),
  type VARCHAR(100),
  medidas VARCHAR(255),
  material VARCHAR(255),
  pintura VARCHAR(255),
  color VARCHAR(255),
  laqueado VARCHAR(255),
  cantidad BIGINT DEFAULT 1,
  startdate DATE,
  fechaentrega DATE,
  fechaestimada DATE,
  foto VARCHAR(500),
  notas TEXT,
  precio NUMERIC(12,2) NOT NULL,
  cogs_amount NUMERIC(12,2),
  pagostatus VARCHAR(50),
  client_email VARCHAR(255),
  metadata JSONB
);
CREATE INDEX idx_invoices_tenant ON invoices(tenant_id);
CREATE INDEX idx_invoices_startdate ON invoices(startdate);
CREATE INDEX idx_invoices_type ON invoices(type);

CREATE TABLE invoice_line_items (
  id BIGSERIAL PRIMARY KEY,
  invoice_id BIGINT NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
  description VARCHAR(255) NOT NULL,
  quantity NUMERIC(12,3) NOT NULL,
  unit_price NUMERIC(12,2) NOT NULL,
  subtotal NUMERIC(12,2) NOT NULL
);
CREATE INDEX idx_invoice_line_items_invoice ON invoice_line_items(invoice_id);

CREATE TABLE work_orders (
  id BIGSERIAL PRIMARY KEY,
  invoice_id BIGINT NOT NULL UNIQUE REFERENCES invoices(id),
  status VARCHAR(50),
  update_at TIMESTAMP
);
CREATE INDEX idx_work_orders_status ON work_orders(status);

CREATE TABLE pagos (
  id BIGSERIAL PRIMARY KEY,
  invoice_id BIGINT NOT NULL REFERENCES invoices(id),
  tenant_id BIGINT REFERENCES tenants(id),
  type VARCHAR(50),
  valor NUMERIC(12,2),
  fecha DATE,
  payment_method VARCHAR(50),
  receipt_path VARCHAR(500)
);
CREATE INDEX idx_pagos_fecha ON pagos(fecha);
CREATE INDEX idx_pagos_tenant ON pagos(tenant_id);

CREATE TABLE payment_schedule (
  id BIGSERIAL PRIMARY KEY,
  invoice_id BIGINT NOT NULL REFERENCES invoices(id),
  tenant_id BIGINT NOT NULL REFERENCES tenants(id),
  expected_date DATE NOT NULL,
  amount NUMERIC(12,2) NOT NULL,
  percentage NUMERIC(5,2),
  status VARCHAR(50) DEFAULT 'PENDIENTE'
);

CREATE TABLE payment_reminder (
  id BIGSERIAL PRIMARY KEY,
  schedule_id BIGINT NOT NULL REFERENCES payment_schedule(id),
  customer_id BIGINT NOT NULL REFERENCES customers(id),
  tenant_id BIGINT NOT NULL REFERENCES tenants(id),
  channel VARCHAR(20) NOT NULL,
  sent_at TIMESTAMP,
  message TEXT,
  response TEXT,
  responded_at TIMESTAMP,
  status VARCHAR(30) DEFAULT 'SENT'
);

CREATE TABLE costos (
  id BIGSERIAL PRIMARY KEY,
  tenant_id BIGINT REFERENCES tenants(id),
  tipo VARCHAR(100),
  fecha DATE,
  valor NUMERIC(12,2) NOT NULL,
  fechacreado TIMESTAMP,
  frequencia VARCHAR(50),
  asunto VARCHAR(255)
);
CREATE INDEX idx_costos_fecha ON costos(fecha);

CREATE TABLE payment_options (
  id BIGSERIAL PRIMARY KEY,
  category VARCHAR(50) NOT NULL,
  code VARCHAR(50) NOT NULL,
  label VARCHAR(100) NOT NULL
);
