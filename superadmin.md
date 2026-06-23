# RBAC: Superadmin (platform) vs Admin (tenant) Separation

## Context

FinanzasFlow is a multi-tenant financial SaaS. The intended trust boundary (per the
client/go-live model) is: the **platform operator (SUPER_ADMIN)** manages tenants and
sees *operational* data only, while each **tenant ADMIN** owns their tenant's *financial*
data and users. Today this boundary is violated:

- `SecurityConfig` explicitly grants **SUPER_ADMIN** access to `/api/costs/**` and
  `/api/finance/**` (lines 95–100) — superadmin can read tenant financials.
- The superadmin's only view (`OperatorController` → `OperatorPage.jsx`) reports
  `totalOwed` (Σ invoice prices), `totalInvoices`, and a `PaymentSchedule` action-queue —
  all financial/economic data the operator must NOT see.
- `AdminController` is shared by ADMIN+SUPER_ADMIN and branches fragilely on
  `tenantId == null`; SUPER_ADMIN has `tenant = null`, so every tenant-scoped service
  receives a null tenant (data-leak / NPE risk).
- There is **no usage/activity data** to power "actions per month" and **no tenant
  management API** (TenantRepo only has `findBySlug`).

**Goal:** Enforce the two-role boundary, give superadmin non-financial operational
metrics (tenant info, user counts, actions/month, last activity), add an activity log
to source that metric, and give superadmin in-app tenant CRUD (create/edit/deactivate).
Decisions confirmed with user: **add a dedicated activity-log table** and **include
tenant CRUD** in this change.

GESTOR is a tenant-level finance user; its current access is preserved unchanged.

---

## Backend

### 1. Authorization boundary — `config/SecurityConfig.java`
Remove `SUPER_ADMIN` from every financial matcher so superadmin is blocked at the
security layer (cleaner than per-service null checks):
- `/api/costs/**` (GET/POST/PUT/DELETE) → `hasAuthority("ADMIN")` (drop SUPER_ADMIN).
- `/api/finance/**` → `hasAnyAuthority("ADMIN","GESTOR")` (drop SUPER_ADMIN).
- `/api/invoices/**`, `/api/customers/**`, `/api/payments/**`, `/api/workorders/**`,
  `/api/ai/**`, `/api/payment-options/**` → drop SUPER_ADMIN (keep ADMIN, GESTOR).
- `/api/admin/**` (all methods) → `hasAuthority("ADMIN")` (drop SUPER_ADMIN; admin is
  now strictly tenant-scoped).
- `/api/operator/**` stays `hasAuthority("SUPER_ADMIN")` and gains the new CRUD routes.
- `/api/users/registro` keep `ADMIN, SUPER_ADMIN` (superadmin needs it for the
  create-tenant flow's first admin).

### 2. `AdminController` → ADMIN-only, tenant-scoped
- Change class `@PreAuthorize` to `hasAuthority('ADMIN')`.
- Remove the `tenantId == null` fallback branches in `getAllUsers` and `getSummary`
  (superadmin no longer reaches this controller). Keep `canManageRole`/`canManagePassword`
  guards as-is.

### 3. `AppUserService.getUsersForTenant(...)` (~line 190)
Add an **explicit** filter excluding `AppUserRole.SUPER_ADMIN` so a tenant admin can
never see superadmin accounts even if one is mis-assigned a tenant (today exclusion is
incidental — superadmins have null tenant).

### 4. Activity log (new) — sources "actions per month"
- **Migration** `V7__create_tenant_activity_log.sql`: table `tenant_activity`
  (`id`, `tenant_id` FK, `action_type` varchar, `actor_user_id` nullable, `created_at`
  timestamptz), index `(tenant_id, created_at)`. **No amounts stored** — only the kind of
  action, so the log itself never leaks financial data to superadmin.
- **Entity** `model/TenantActivity.java` + **repo** `TenantActivityRepo` with
  `countByTenant_IdAndCreatedAtBetween(...)` and
  `findTop20ByTenant_IdOrderByCreatedAtDesc(...)`.
- **Service** `service/ActivityLogService.record(tenantId, actionType, actorUserId)`.
  Call it from the existing creation paths (reuse, don't duplicate):
  `InvoiceService.createForTenant`, `CostService.createForTenant`, customer creation in
  `CustomerService`, and `LedgerIngestionService.finalizeDirection` (Telegram ingestion).
  Action types e.g. `INVOICE_CREATED`, `COST_CREATED`, `CUSTOMER_CREATED`,
  `TELEGRAM_INGESTION`.

### 5. Tenant entity + lifecycle — `model/Tenant.java`
- Add `active` boolean (default true) via the same V7 migration (`ALTER TABLE tenants`).
- Enforce on login: in the login path (`AppUserService` / `JwtAuthenticationFilter`),
  reject auth for users whose tenant is inactive (superadmin has no tenant → unaffected).

### 6. Superadmin endpoints — `controller/OperatorController.java`
Replace the financial summary and add CRUD (all `hasAuthority('SUPER_ADMIN')`):
- `GET /api/operator/tenants` → per-tenant **operational** summary only:
  `{ id, name, email, phone, active, createdAt, userCount, actionsThisMonth,
  lastActivityAt }`. **Remove `totalOwed`, `totalInvoices`, `avgDSO`** and the
  `Invoice`/`InvoiceRepo` dependency. Source counts from `AppUserService`/`UserRepo`
  (tenant user count) and `TenantActivityRepo` (month count + last activity).
- Replace `GET /tenants/{id}/action-queue` (returns financial `PaymentSchedule`) with
  `GET /tenants/{id}/activity` → recent `tenant_activity` rows (type + timestamp, no
  amounts).
- `POST /api/operator/tenants` → create tenant (name/email/phone, generate `slug`) **and**
  its first ADMIN user in one transaction (reuse `AppUserService` registration).
- `PUT /api/operator/tenants/{id}` → edit name/email/phone.
- `PUT /api/operator/tenants/{id}/active` → activate/deactivate.
- Back these with a new `TenantService` (TenantRepo has no CRUD helpers today).

---

## Frontend  (`frontEnd/workflow/src`)

### 7. `views/OperatorPage.jsx`
- Remove financial display: the **"Total facturado" (`totalOwed`)** and **"Facturas"**
  metrics (lines ~74–75) and the `PaymentSchedule` action table (lines ~86–90).
- Show operational metrics per tenant card: Usuarios, Acciones este mes, Última
  actividad, Estado (activo/inactivo).
- Add tenant **create** (modal/form: name, email, phone, first-admin username+password)
  and **edit/deactivate** actions, calling the new `/api/operator/tenants` routes.
- Replace the action-queue panel with a non-financial recent-activity feed
  (`/tenants/{id}/activity`).
- Routing/role guards already correct: `RoleRoute.jsx` sends SUPER_ADMIN→`/operator`,
  ADMIN/GESTOR→`/finance`; sidebar already splits nav by role — no changes needed.

---

## Verification

Backend (build + migrate): `cd backEnd && ./mvnw -q clean package` (runs Flyway V7 on boot).
End-to-end role checks (curl or UI):
- **SUPER_ADMIN** JWT: `GET /api/costs` → **403**; `GET /api/finance/**` → **403**;
  `GET /api/operator/tenants` → returns operational fields only, **no** `totalOwed`.
- **SUPER_ADMIN** `POST /api/operator/tenants` → new tenant + admin created; the tenant
  appears in the list with `userCount=1`, `actionsThisMonth=0`.
- **ADMIN** JWT: `/api/finance` & `/api/costs` work; `GET /api/operator/tenants` → **403**;
  `GET /api/admin/users` → list **excludes** SUPER_ADMIN accounts.
- Create an invoice/cost as ADMIN → `actionsThisMonth` for that tenant increments;
  `lastActivityAt` updates; **no amount** is exposed on any `/api/operator/**` response.
- Deactivate a tenant → its users can no longer log in; superadmin unaffected.
- Frontend: superadmin `/operator` shows no money figures; ADMIN finance views unchanged.

## Notes / out of scope
- This overlaps with the separately-tracked "SUPER_ADMIN create-client flow" — the
  tenant CRUD here fulfills it; reconcile that go-live item afterward.
- GESTOR permissions intentionally unchanged.
- Existing `CostController.currentTenantId()` RuntimeException-on-null guard becomes
  redundant (superadmin blocked upstream) but is kept as defense-in-depth.
