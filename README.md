# FinanzasFlow

FinanzasFlow is a B2B financial workspace for managing invoices, customers, collections follow-up, payments, costs, finance dashboards, and AI-assisted business analysis.

The project is in the middle of a domain cleanup from the old furniture workflow. The frontend has been converted to the FinanzasFlow invoice/cobranzas experience; the backend compiles and exposes invoice-compatible routes, while some backend inventory cleanup remains tracked in `FinanzasFlow.md`.

## What It Does

- **Invoices**: create, edit, list, search, and inspect invoices.
- **Customers**: attach invoices to customer records and show customer context in invoice detail.
- **Collections follow-up**: track invoice workflow status with `EN_GESTION`, `CONTACTADO`, `PROMETIO_PAGO`, `EN_DISPUTA`, `INCOBRABLE`, and `CERRADO`.
- **Payments**: record payments and see collected totals and outstanding balances.
- **Finance dashboard**: view revenue, costs, projections, and AI-generated financial insights.
- **Admin tools**: manage users, roles, high-level metrics, and weekly AI summaries.
- **Role-based access**: supports `USER`, `SELLER`, `ADMIN`, `VIEWER`, and `SUPER_ADMIN` surfaces.

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Spring Boot 3.5 / Java 21 |
| Database | PostgreSQL with Flyway migrations |
| Auth | Spring Security + JWT |
| Frontend | React 19 / Create React App |
| Charts | Recharts |
| AI | Anthropic API |
| Automation | N8N webhook support |

## Project Layout

```text
backEnd/                  Spring Boot API
frontEnd/muebles_workflow React frontend app, package name finanzas-flow
FinanzasFlow.md           Cleanup/status plan for the domain migration
refactorplan.md           Earlier backend refactor notes
```

## Run Locally

### Backend

```bash
cd backEnd
./mvnw.cmd spring-boot:run
```

The backend runs on `http://localhost:8080` by default.

If Maven cannot find Java on Windows, set `JAVA_HOME` first:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
.\mvnw.cmd spring-boot:run
```

### Frontend

```bash
cd frontEnd/muebles_workflow
npm install
npm.cmd start
```

The frontend runs on `http://localhost:3000`.

Use `npm.cmd` in PowerShell if `npm.ps1` is blocked by execution policy.

## Build Checks

Backend compile:

```bash
cd backEnd
./mvnw.cmd clean compile -DskipTests
```

Frontend production build:

```bash
cd frontEnd/workflow
npm.cmd run build
```

Both were last verified successfully during the FinanzasFlow frontend cleanup.

## Environment

Backend configuration lives in:

```text
backEnd/src/main/resources/application.properties
```

Expected environment-backed configuration includes:

```properties
DATABASE_URL=jdbc:postgresql://<host>/<db>
DATABASE_USERNAME=<user>
DATABASE_PASSWORD=<password>
DATABASE_DRIVER=org.postgresql.Driver
HIBERNATE_DIALECT=org.hibernate.dialect.PostgreSQLDialect
JWT_SECRET=<secret>
ANTHROPIC_API_KEY=<optional>
CLOUDINARY_CLOUD_NAME=<optional>
CLOUDINARY_API_KEY=<optional>
CLOUDINARY_API_SECRET=<optional>
N8N_WEBHOOK_PRODUCT_CREATED=<optional>
TELEGRAM_BOT_TOKEN=<optional>
TELEGRAM_ADMIN_CHAT_IDS=<optional comma-separated Telegram chat IDs>
TELEGRAM_REMINDERS_CRON=<optional, default 0 0 9 * * *>
```

Frontend environment lives in:

```text
frontEnd/muebles_workflow/.env
```

```properties
REACT_APP_TEST_URL=http://localhost:8080
REACT_APP_PROD_URL=<production-api-url>
REACT_APP_NAME=FinanzasFlow
```

## Notes

- The backend still maps invoice endpoints under both `/api/invoices` and legacy `/api/products` for compatibility.
- The frontend now uses `/api/invoices`.
- Inventory and product-template backend removal is still pending unless completed separately.
