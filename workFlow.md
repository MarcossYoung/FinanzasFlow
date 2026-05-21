# FinanzasFlow Current Application Workflow

Last updated: May 19, 2026

## 1. Application Purpose

FinanzasFlow is a B2B collections and finance workflow application. It helps the team manage customers, invoices, payment status, overdue accounts, finance visibility, AI summaries, Telegram admin actions, and reminder automation.

The current product is centered on invoices and collections. The old inventory/material/product-template stack has been removed from the backend.

## 2. Roles And Access

### SUPER_ADMIN
- Internal role only.
- Kept for us and hidden from client-facing role selection.
- Can access admin-level functionality.
- Can use operator-only backend routes.

### ADMIN
- Can manage users.
- Can view finance dashboard.
- Can manage costs.
- Can manage customers.
- Can create, edit, delete, and manage invoices.

### GESTOR
- Can manage invoices and customers.
- Can view finance information relevant to their work.
- Can update collection status and add payments.
- Cannot access cost management or admin user management.

## 3. Authentication Flow

1. User logs in from `/login`.
2. Frontend sends credentials to `POST /api/users/login`.
3. Backend authenticates with Spring Security.
4. Backend returns JWT and user role.
5. Frontend stores token and sends it as `Authorization: Bearer <token>`.
6. Protected routes use role guards:
   - `/dashboard`
   - `/finance`
   - `/customers`
   - `/costs`
   - `/admin`
   - `/invoices/:invoiceId`
   - `/invoices/:invoiceId/edit`

## 4. Main Navigation

### Facturas
Route: `/dashboard`

This is the main working screen for collections.

Dashboard tabs:
- All invoices
- Due soon
- Unpaid / promised payment
- Overdue / disputed

### Finanzas
Route: `/finance`

Shows financial KPIs, charts, AI insight, and PDF export.

### Clientes
Route: `/customers`

Customer management UI.

### Costos
Route: `/costs`

Admin-only operational cost management.

### Panel Admin
Route: `/admin`

Admin-only user management and summary view.

### Perfil
Route: `/profile/:id`

User profile screen.

## 5. Customer Workflow

Route: `/customers`

Backend:
- `GET /api/customers`
- `GET /api/customers/search?q=...`
- `POST /api/customers`
- `PUT /api/customers/{id}`
- `DELETE /api/customers/{id}`

Frontend workflow:
1. Admin or gestor opens Clientes.
2. Existing customers are listed in a table.
3. User can search customers.
4. User can create a new customer with:
   - Name
   - CUIT/DNI
   - Email
   - Phone
   - Notes
   - Payment score
5. User can edit a customer from the table.
6. User can delete a customer.
7. Customers can be assigned to invoices during invoice create/edit.

## 6. Invoice Workflow

Routes:
- List: `/dashboard`
- Detail: `/invoices/:invoiceId`
- Edit: `/invoices/:invoiceId/edit`

Backend:
- `GET /api/invoices`
- `GET /api/invoices/search`
- `POST /api/invoices/create`
- `GET /api/invoices/{id}`
- `PUT /api/invoices/{id}`
- `DELETE /api/invoices/{id}`
- `GET /api/invoices/types`
- `GET /api/invoices/due-this-week`
- `GET /api/invoices/past-due`
- `GET /api/invoices/not-picked-up`
- `GET /api/invoices/filter`

### Create Invoice

1. User opens invoice create modal/form.
2. Frontend loads:
   - Invoice types
   - Customers
3. User enters:
   - Title
   - Type
   - Customer
   - Contact phone
   - Due date
   - Notes
   - Initial payment amount
   - Line items
4. Line items contain:
   - Description
   - Quantity
   - Unit price
   - Subtotal
5. Frontend computes invoice total from line items.
6. Backend also computes `precio` from line items when rows are provided.
7. Backend creates:
   - Invoice
   - Invoice line items
   - Initial work order with status `EN_GESTION`
   - Optional initial payment if amount was provided
8. Invoice appears in dashboard tables.

### Edit Invoice

1. User opens invoice detail.
2. User clicks Edit.
3. User can update:
   - Customer
   - Contact phone
   - Type
   - Due date
   - Notes
   - Work order status
   - Line items
4. Backend replaces invoice line items from submitted rows.
5. Backend recomputes total from line items.

### Invoice Detail

Shows:
- Invoice data
- Customer data
- Current collection status
- Line item table
- Payment table
- Total paid
- Remaining balance

User can:
- Change collection status
- Add payment
- Navigate to edit screen

## 7. Collection Status Workflow

Statuses:
- `EN_GESTION`
- `CONTACTADO`
- `PROMETIO_PAGO`
- `EN_DISPUTA`
- `INCOBRABLE`
- `CERRADO`

Status can be updated from:
- Invoice detail page
- Invoice edit page
- Telegram admin bot command

The status lives on the invoice work order.

## 8. Payment Workflow

Backend:
- `GET /api/payments/{invoiceId}`
- `POST /api/payments`
- Receipt upload/download endpoints exist.

Current flow:
1. User opens invoice detail.
2. User enters payment amount and type.
3. Frontend posts payment.
4. Backend stores payment against invoice.
5. Invoice detail refreshes.
6. Balance is calculated as invoice total minus total paid.

## 9. Finance Workflow

Route: `/finance`

Backend:
- `GET /api/finance?from=YYYY-MM-DD&to=YYYY-MM-DD`
- `GET /api/finance/projection`

Admin view shows:
- Total income
- COGS / CMV
- Gross profit
- Operating expenses
- Cash received
- Net profit
- User performance chart
- Expense distribution chart
- AI insight
- PDF export

Gestor view shows:
- Personal income
- Units sold

### Finance AI Insight

1. User selects a month.
2. User clicks Analyze.
3. Frontend calls `POST /api/ai/finance-insight`.
4. Backend summarizes finance state using current dashboard data.
5. Insight appears in the finance dashboard.

### Finance PDF Export

1. User clicks Exportar PDF.
2. Browser print/PDF flow opens.
3. Print-only report layout is rendered.
4. Report includes:
   - Period
   - Generated timestamp
   - User
   - KPI summary
   - AI insight text
   - Charts
5. User saves as PDF from browser dialog.

## 10. Costs Workflow

Route: `/costs`

Admin-only.

Backend:
- `GET /api/costs`
- `POST /api/costs`
- `DELETE /api/costs/{id}`

Costs feed finance calculations and reports.

## 11. AI Workflow

Backend:
- `POST /api/ai/finance-insight`
- `POST /api/ai/parse-order`
- `POST /api/ai/parse-search`
- `GET /api/ai/weekly-digest`
- `POST /api/ai/chat`

Current AI responsibilities:
- Finance insights
- Weekly digest
- Invoice text parsing
- Search/filter parsing
- Contextual chat support

Current parsing domain:
- Invoice/customer/status/amount filters
- Invoice fields
- Line items

Image invoice parsing is not implemented yet. Telegram image handling currently acknowledges the image and leaves Claude Vision parsing for a later step.

## 12. Telegram Admin Bot Workflow

Backend:
- `POST /api/telegram/webhook`

Config:
- `TELEGRAM_BOT_TOKEN`
- `TELEGRAM_ADMIN_CHAT_IDS`

If `TELEGRAM_ADMIN_CHAT_IDS` is set, only those chat IDs can run admin commands.
If empty, local/dev accepts admin commands for easier testing.

Admin commands:
- `/help`
- `/overdue` or `/vencidas`
- `/status <invoiceId> <STATUS>`
- `/note <invoiceId> <note text>`

### Query Overdue

1. Admin sends `/overdue`.
2. Backend finds overdue open invoices.
3. Bot replies with count, total, and invoice summary list.

### Update Status

1. Admin sends `/status 123 CONTACTADO`.
2. Backend finds invoice work order.
3. Backend updates status and timestamp.
4. Bot confirms the update.

### Create Note

1. Admin sends `/note 123 Cliente pidio llamar el viernes`.
2. Backend appends a timestamped Telegram note to invoice notes.
3. Bot confirms note creation.

### Image Placeholder

1. Admin sends photo/image.
2. Backend replies that image was received.
3. Claude Vision invoice parsing is planned later.

## 13. Reminder Workflow

Backend scheduler:
- Enabled by `@EnableScheduling`
- `ReminderEngine`
- Default cron: `0 0 9 * * *`
- Config override: `TELEGRAM_REMINDERS_CRON`

Workflow:
1. Scheduler runs daily.
2. Backend finds overdue open invoices.
3. If invoice has no open payment schedule, backend creates a `VENCIDO` payment schedule.
4. Backend sends reminders for:
   - Pending schedules close to due date
   - Overdue schedules
5. Each send creates a `PaymentReminder`.
6. Duplicate reminders for the same schedule on the same day are skipped.
7. If customer Telegram delivery fails or customer data is missing, backend sends an admin prompt to configured admin chat IDs.
8. Customer replies can still be captured through the Telegram webhook and attached to the latest reminder.

## 14. Super Admin / Operator Workflow

Backend:
- `/api/operator/**`

SUPER_ADMIN can access internal operator routes.

Current use:
- Tenant overview
- Tenant action queue

This is internal and not exposed as a normal client-facing workflow.

## 15. Current Known Boundaries

Implemented:
- Auth and roles
- Customer management UI
- Invoice CRUD
- Invoice line items
- Payments
- Finance dashboard
- Finance AI insight
- Finance PDF export through browser print
- Admin and gestor workflows
- Telegram admin text commands
- Reminder scheduling
- Hidden SUPER_ADMIN role

Not yet implemented:
- Claude Vision invoice parsing from Telegram images
- Fully automated PDF generation without browser print dialog
- Advanced reminder message generation with AI-personalized copy
- Full tenant/operator UI on the frontend

