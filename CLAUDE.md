# FinanzasFlow

B2B financial workspace for managing **invoices, customers, collections follow-up
(cobranzas), payments, costs, finance dashboards, and AI-assisted analysis**.
Multi-tenant SaaS. (The repo was migrated from an older furniture-workflow app —
ignore any leftover "muebles"/furniture naming; some backend cleanup is still
tracked in `FinanzasFlow.md`.)

## Tech Stack

| Layer | Technology | Version |
|---|---|---|
| Backend | Spring Boot | 3.5.12 |
| Backend Language | Java | 21 |
| Backend Build | Maven (wrapper: `mvnw`) | - |
| Database | PostgreSQL (Neon cloud), Flyway migrations | V1–V9 |
| ORM | Spring Data JPA / Hibernate | - |
| Auth | Spring Security + JWT | - |
| Multi-tenancy | `config/TenantContext.java` (ThreadLocal) | - |
| Frontend | React (Create React App) | 19.2.0 |
| Frontend Language | JavaScript/JSX | ES6+ |
| HTTP Client | Axios | 1.12.2 |
| Charts | Recharts | 3.3.0 |
| Routing | React Router DOM | 7.9.4 |
| AI | Anthropic API (Claude) | - |

External: Telegram bot (ledger ingestion via webhook), N8N webhook support,
Railway (deploy), Neon (DB).

## Project Structure

```
FinanzasFlow/
├── backEnd/src/main/java/com/example/demo/
│   ├── config/         # SecurityConfig, JWT filter, TenantContext, WebConfig
│   ├── controller/     # REST endpoints (thin — delegate to services)
│   ├── service/        # Business logic layer
│   ├── repository/     # Spring Data JPA interfaces + native queries
│   ├── model/          # JPA entities + Enums
│   ├── dto/            # Request/Response DTOs (decouple API from entities)
│   └── exceptions/     # GlobalExceptions (@ControllerAdvice) + typed exceptions
├── backEnd/src/main/resources/
│   ├── application.properties      # DB, JWT, CORS, server config
│   └── db/migration/               # Flyway V1..V9
├── frontEnd/workflow/              # React app (package name: finanzas-flow)
│   └── src/
│       ├── api/                    # base URL + axios config
│       ├── components/             # Reusable UI components
│       ├── views/                  # Page-level components (invoices, customers,
│       │                           #   finances, dashboard, CostsManager, admin…)
│       ├── App.js                  # Router setup
│       ├── RoleRoute.jsx / AdminRoute.js  # Route guards
│       ├── InvoicesContext.jsx     # Global invoices state (Context API)
│       └── UserProvider.js         # Auth state + Axios default headers
└── plans/docs/                     # Plans & the dev-team charter (devTeam.md)
```

## Build & Run Commands

### Backend
```bash
./mvnw spring-boot:run     # Start on :8080
./mvnw -B clean verify     # Build + run tests (tests use H2 in-memory)
./mvnw test                # Run tests
```

### Frontend
```bash
cd frontEnd/workflow
npm start                  # Dev server on :3000
npm run build              # Production build → /build
CI=true npm test -- --watchAll=false   # Jest (CI-safe)
```

### Ports
- Frontend: `http://localhost:3000`
- Backend: `http://localhost:8080`
- DB: Remote Neon PostgreSQL (see `application.properties`; H2 fallback for tests)

## Domain Model (brief)

Invoicing/payments ERP. Core entities (`backEnd/.../model/`): `Invoice` +
`InvoiceLineItem`, `Customer`, `Costs`/`CostType`, `OrderPayments` /
`PaymentSchedule` / `PaymentReminder`, `Tenant` + `TenantActivity`, `AppUser`.
Telegram ledger ingestion: `TelegramConnection`, `LedgerDirection`,
`LedgerRecordType`. **Everything is tenant-scoped** — always respect the active
tenant (`TenantContext`).

Collections (cobranzas) workflow statuses: `EN_GESTION`, `CONTACTADO`,
`PROMETIO_PAGO`, `EN_DISPUTA`, `INCOBRABLE`, `CERRADO`.

Backend roles (`AppUserRole`): `ADMIN`, `GESTOR`, `SUPER_ADMIN`.

## Key Config Locations

- DB / JWT / CORS / server: `backEnd/src/main/resources/application.properties`
- Security rules & public routes: `backEnd/.../config/SecurityConfig.java`
- Tenant resolution: `backEnd/.../config/TenantContext.java`
- Frontend base URL / axios: `frontEnd/workflow/src/api/`

## Dev Team

This project has an agentic dev team (`.claude/agents/` + the `/team` command).
See `plans/docs/devTeam.md` (the team charter) for the roster, orchestration
model, and the rule that **no change merges to `main` without a clean
`/security-review`**.
