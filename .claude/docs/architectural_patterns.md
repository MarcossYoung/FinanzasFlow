# Architectural Patterns

Patterns confirmed across multiple files in MueblesWorkFlow.

---

## 1. Layered Architecture (Backend)

Every feature follows: `Controller → Service → Repository → Entity`.

- Controllers are thin: validate input, delegate to service, return DTO.
- Services own all business logic and transaction boundaries.
- Repositories contain only data access (JPA methods + custom queries).

Examples spanning the full stack:
- `ProductController.java` → `ProductService.java` → `ProductRepo.java` → `Product.java`
- `FinanceController.java` → `FinanceService.java` → `ProductRepo.java`, `CostRepo.java`
- `PaymentsController.java` → `PaymentService.java` → `PaymentRepo.java`

---

## 2. Constructor Injection (Backend)

All services use constructor-based dependency injection, not `@Autowired` field injection. This makes dependencies explicit and testable.

Reference: `backEnd/.../service/ProductService.java:30-38`

```java
public ProductService(ProductRepo productRepo,
                      WorkOrderRepo workOrderRepo,
                      AppUserService userService,
                      PaymentRepo orderPaymentsRepo) { ... }
```

Same pattern in: `FinanceService.java`, `PaymentService.java`, `WorkOrderService.java`.

---

## 3. DTO Pattern (Backend)

Entities are never returned directly from controllers. Separate DTOs decouple the API surface from the persistence model.

| DTO | Purpose |
|---|---|
| `ProductResponse.java` | Read responses |
| `ProductCreateRequest.java` | Creation payloads |
| `ProductUpdateDto.java` | Update payloads |
| `CreatePaymentRequest.java` | Payment creation |
| `FinanceDashboardResponse.java` | Finance dashboard |
| `MonthlyAmountRow.java` | Native query projection |

---

## 4. JWT Authentication Flow

**Backend filter chain** (`JwtAuthenticationFilter.java`):
1. Skips OPTIONS requests (CORS preflight).
2. Extracts token from `Authorization: Bearer {token}` header.
3. Validates via `JwtTokenUtil` (HMAC-SHA256, 10h expiry).
4. Sets `SecurityContext` with authenticated principal.

Reference: `backEnd/.../config/JwtAuthenticationFilter.java:29-80`

**Public routes** (no auth required, `SecurityConfig.java:76-77`):
- `POST /api/users/registro`
- `POST /api/users/login`
- `GET /api/products`

**Role enforcement**: `@hasAuthority()` annotations on controller methods. Admin-only paths protected via `SecurityConfig`.

**Frontend token handling** (`UserProvider.js:17-18`):
- Token stored in `localStorage` as `"token"`.
- Set as Axios default header on app init: `Authorization: Bearer {token}`.

---

## 5. React Context API for Global State

Two contexts manage shared state across the component tree:

**`OrdersContext.jsx`** — orders list, loading state, error state.
- Guards against double-fetches: `didInitRef` + `inFlightRef`.
- Injects auth token per-request from localStorage.
- Exposes `refreshOrders()` for manual invalidation.

**`UserProvider.js`** — authenticated user object + JWT token.
- Initializes Axios default headers at startup.
- Exposes `initialized` flag so children don't render before hydration.

Both contexts wrap the app in `App.js` and are consumed via `useContext()` hooks in views and components.

---

## 6. Lazy Loading (Frontend)

All page-level views are lazy loaded via `React.lazy()` + `Suspense` with a `<Loader />` fallback.

Reference: `frontEnd/.../src/App.js:21-33`

```js
const Dashboard = lazy(() => import('./views/dashboard'));
const Finances  = lazy(() => import('./views/finances'));
```

Every route is wrapped in `<Suspense fallback={<Loader />}>`. This applies to all ~12 views under `views/`.

---

## 7. Native SQL Queries with Projections (Backend)

When standard JPA queries are insufficient (aggregations, joins across tables), repositories use `@Query(nativeQuery = true)` with interface or `Map<String, Object>` projections.

Reference: `backEnd/.../repository/ProductRepo.java:26-54`

- `incomeByMonth(...)` → returns `List<MonthlyAmountRow>` (interface projection)
- `getUserPerformanceData(...)` → returns `List<Map<String, Object>>`

The `MonthlyAmountRow` interface projection (`dto/MonthlyAmountRow.java`) maps column aliases directly without a concrete class.

---

## 8. Role-Based Route Protection (Frontend)

Three layers of route protection:
1. `protectedRoute.js` — checks for authenticated user (any role).
2. `AdminRoute.js` — checks for `ADMIN` role.
3. `RoleRoute.jsx` — checks for a configurable set of roles.

These wrap `<Route>` elements in `App.js`. The role is sourced from the user object in `UserProvider` context.

---

## 9. Modal Pattern

Modals use click propagation to close on outside click without extra state.

Reference: `frontEnd/.../components/productCreationModular.jsx:8-13`

```jsx
<div className='modal-overlay' onClick={onClose}>
  <div className='modal-content' onClick={(e) => e.stopPropagation()}>
    ...
  </div>
</div>
```

Reused in product creation and editing flows.

---

## 10. External Webhook Integration (N8N Chatbot)

`chatBot.jsx` integrates with N8N via a single POST webhook. Key design choices:

- **Session persistence**: UUID generated once and stored in `localStorage` (`chatBot.jsx:18`).
- **Stateful conversation**: `sessionId` sent with every message body.
- **Response normalization**: handles both array and object N8N response shapes (`chatBot.jsx:61-71`).
- **Optimistic UI**: typing indicator shown immediately while awaiting response.

Reference: `frontEnd/.../components/chatBot.jsx:1-152`
