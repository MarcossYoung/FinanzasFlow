# MueblesWorkFlow

SaaS application for furniture production workflow management. Tracks orders from creation through delivery, manages payments, monitors finances, and provides an AI chatbot (N8N) for querying business data.

## Tech Stack

| Layer | Technology | Version |
|---|---|---|
| Backend | Spring Boot | 3.3.3 |
| Backend Language | Java | 17 |
| Backend Build | Maven (wrapper: `mvnw`) | - |
| Database | PostgreSQL (Neon cloud) | - |
| ORM | Spring Data JPA / Hibernate | - |
| Auth | Spring Security + JWT (JJWT 0.11.5) | - |
| Frontend | React (Create React App) | 19.2.0 |
| Frontend Language | JavaScript/JSX | ES6+ |
| HTTP Client | Axios | 1.12.2 |
| Charts | Recharts | 3.3.0 |
| Routing | React Router DOM | 7.9.4 |

External: N8N webhook for AI chatbot, Netlify for frontend deployment.

## Project Structure

```
MueblesWorkFlow/
├── backEnd/src/main/java/com/example/demo/
│   ├── config/         # SecurityConfig, JWT filter, WebConfig
│   ├── controller/     # REST endpoints (thin — delegate to services)
│   ├── service/        # Business logic layer
│   ├── repository/     # Spring Data JPA interfaces + native queries
│   ├── model/          # JPA entities + Enums/
│   ├── dto/            # Request/Response DTOs (decouple API from entities)
│   └── exceptions/     # GlobalExceptions (@ControllerAdvice) + typed exceptions
├── backEnd/src/main/resources/
│   └── application.properties   # DB, JWT, CORS, server config
├── frontEnd/muebles_workflow/src/
│   ├── api/config.js            # BASE_URL (env-aware)
│   ├── components/              # Reusable UI components
│   ├── views/                   # Page-level components (lazy loaded)
│   ├── App.js                   # Router setup with lazy imports + Suspense
│   ├── OrdersContext.jsx        # Global orders state (Context API)
│   └── UserProvider.js          # Auth state + Axios default headers
└── frontEnd/muebles_workflow/public/
    └── _redirects               # Netlify SPA routing
```

## Build & Run Commands

### Backend
```bash
./mvnw spring-boot:run     # Start on :8080
./mvnw clean install       # Build (skips tests by default with -DskipTests)
./mvnw test                # Run tests
```

### Frontend
```bash
cd frontEnd/muebles_workflow
npm start                  # Dev server on :3000
npm run build              # Production build → /build
npm test                   # Jest test runner
```

### Ports
- Frontend: `http://localhost:3000`
- Backend: `http://localhost:8080`
- DB: Remote Neon PostgreSQL (see `application.properties`)

## Domain Model (brief)

`Product` is the central entity (a furniture order). It has:
- 1:1 → `WorkOrder` (production status)
- 1:N → `OrderPayments` (deposits, finals, partials)
- N:1 → `AppUser` (owner/seller)

Roles: `ADMIN`, `SELLER`, `USER` (defined in `AppUserRole.java`).

## Role-Based Frontend Access

| Feature | USER | SELLER | ADMIN |
|---|---|---|---|
| Sidebar: Pedidos | ✓ | ✓ | ✓ |
| Sidebar: Finanzas | ✗ | ✓ | ✓ |
| Sidebar: Costos | ✗ | ✗ | ✓ |
| Sidebar: Panel Admin | ✗ | ✗ | ✓ |
| Sidebar: Perfil | ✓ | ✓ | ✓ |
| Tab: Pedidos (all orders) | ✗ (redirects to Atrasados) | ✓ | ✓ |
| Tab: Semana actual | ✓ | ✓ | ✓ |
| Tab: Atrasados | ✓ | ✓ | ✓ |
| Tab: No retirados | ✓ | ✓ | ✓ |
| Finance view | ✗ | Own income only | Full dashboard |
| Add payments | ✗ | ✓ | ✓ |

Role checks live in:
- **Route guards**: `App.js` — `RoleRoute` with `allowedRoles`; USER at `/dashboard` index redirects to `/dashboard/late`
- **Tab visibility**: `views/dashboard.jsx` — `isSeller`, `isAdmin` flags gate the "Pedidos" tab
- **Sidebar links**: `components/sidebar.jsx` — `isSeller`, `isAdmin` flags
- **Finance branching**: `views/finances.jsx` — seller sees own-income view, admin sees full dashboard

## Key Config Locations

- Backend URL / CORS: `backEnd/src/main/resources/application.properties`
- Frontend base URL: `frontEnd/muebles_workflow/src/api/config.js`
- Security rules & public routes: `backEnd/.../config/SecurityConfig.java:60-80`
- JWT settings (10h expiry): `backEnd/.../service/JwtTokenUtil.java:24-31`

## Additional Documentation

Check these files when working on the relevant area:

| Topic | File |
|---|---|
| Architectural patterns & design decisions | `.claude/docs/architectural_patterns.md` |
