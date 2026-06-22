# Frontend Cleanup Implementation Plan

## Objective

Align the web application with the easy-tool workflow described in [[easyTool.md]]:

- Telegram is the primary ledger-ingestion path for both invoices (Cobro) and supplier costs (Gasto), per [[easyTool.md]].
- The web application prioritizes financial reporting, invoice review, and internal operations.
- Manual invoice and cost entry remains available as a correction backstop, but is not presented as the normal workflow.
- Existing functionality and role restrictions must remain explicit and testable.

This is a cleanup and information-architecture change, not a visual redesign.

## Verified baseline

The frontend is a Create React App project under `frontEnd/workflow` using React Router and the shared stylesheet at `src/css/styles.css`.

The production build currently succeeds with:

```powershell
npm.cmd run build
```

### Routes

`src/App.js` currently defines:

- Public: `/login`, `/registro`, `/privacy-policy`
- Client views: `/finance`, `/dashboard`, `/customers`
- Invoice views: `/invoices/:invoiceId`, `/invoices/:invoiceId/edit`
- Administration: `/costs`, `/admin`, `/operator`
- Dead redirects: `/products/:productId`, `/inventory`

### Work already completed

The following items from the earlier cleanup draft are already present and must not be reimplemented:

- `CostsManager.jsx` uses the shared page, KPI, form, badge, and pagination classes.
- `finances.jsx` uses `page-header`, `page-title`, `kpi-grid`, and `finance-chart-grid`.
- `styles.css` already defines `.panel`, `.page-title`, `.page-header`, `.kpi-grid`, `.freq-badge`, `.cost-type-badge`, and related Costs/Finance layout classes.
- `.card` no longer forces `display: flex`.
- Invoice editing is already reachable from invoice detail through `/invoices/:invoiceId/edit`.
- `PrivacyPolicy.jsx` contains FinanzasFlow-specific invoice and payment language; no furniture-era copy was found.

### Remaining issues

- Invoice creation controls are rendered by `components/ordersTable.jsx`, not `views/dashboard.jsx`. They currently appear in normal, empty, and filtered invoice states.
- `OperatorPage.jsx` uses Tailwind-style classes, but Tailwind is not part of the project. The view is therefore largely unstyled.
- `dashboard.jsx` still references undefined `flex` and `p-3` utilities.
- `loader.jsx`, `ordersTable.jsx`, `invoiceDetail.js`, `PrivacyPolicy.jsx`, and `adminPage.js` retain inline styles.
- `.loader-spinner` is referenced but not defined.
- Public registration remains routable even though client users are provisioned by administrators.
- Role navigation and redirects are inconsistent:
  - `SUPER_ADMIN` is treated as an admin by the sidebar and can see links to routes that do not allow `SUPER_ADMIN`.
  - The root redirect sends `SUPER_ADMIN` to `/finance`, although `/finance` only allows `ADMIN` and `GESTOR`.
  - The sidebar always shows `/dashboard`, while its invoice content only permits `ADMIN` and `GESTOR`.
- There are no frontend test files covering routing, role navigation, or the manual-entry affordances.

## Implementation scope

### 1. Correct routes and role-specific navigation

#### `src/App.js`

- Remove the lazy import for `views/registro`.
- Remove the public `/registro` route. Keep `views/registro.js` temporarily so removal is reversible and does not mix UI cleanup with API removal.
- Remove `/products/:productId` and `/inventory` redirects.
- Make default destinations role-specific:
  - `SUPER_ADMIN` -> `/operator`
  - `ADMIN` and `GESTOR` -> `/finance`
  - unauthenticated users -> `/login`
- Apply the same role-aware behavior to the wildcard route.
- Keep `/costs` restricted to `ADMIN`.
- Keep `/operator` restricted to `SUPER_ADMIN`.
- Do not broaden role access merely to make currently incorrect links work.

#### `src/components/sidebar.jsx`

- Replace the combined `isAdmin` check with exact role flags.
- For `ADMIN` and `GESTOR`, show the primary client links in this order:
  1. Finanzas
  2. Facturas
  3. Clientes
- Add a visually secondary `Gestion` group:
  - Costos: `ADMIN` only
  - Panel Admin: `ADMIN` only
- Show Operador only to `SUPER_ADMIN`.
- Keep Perfil in the footer for all authenticated users.
- Add semantic sidebar group markup and corresponding CSS instead of encoding spacing with inline styles.

### 2. Demote manual invoice creation

Invoice creation is owned by `components/ordersTable.jsx`, which is reused by the main and filtered invoice views.

#### `src/components/ordersTable.jsx`

- Add an explicit prop such as `allowManualCreate`, defaulting to `false`.
- Render the creation modal and its trigger only when that prop is enabled and the role can edit invoices.
- Replace the prominent `Nueva Factura` language with `Agregar o corregir manualmente`.
- Style the trigger as a secondary action rather than the primary table action.
- Preserve the empty-state explanation, but do not make manual creation the main onboarding message.
- Remove duplicate creation buttons from search-empty and filtered views.
- Replace `window.location.href` navigation with React Router navigation so the application does not perform a full page reload.
- Extract the remaining inline styles into named CSS classes.

#### Invoice list views

- Pass `allowManualCreate` only from the main invoice list (`views/invoicesAll.js`).
- Do not enable it in `invoicesDueSoon`, `invoicesOverdue`, or `invoicesUnpaid`.

#### Existing forms

- Keep `components/invoiceCreationModal.jsx` and `views/invoiceCreateForm.jsx` functionally intact.
- Keep `views/invoiceEditForm.js` and `/invoices/:invoiceId/edit` as the correction path.
- Do not add a standalone invoice-creation route.

### 3. Demote cost entry while preserving reporting

`views/CostsManager.jsx` has already received its main styling cleanup. Only its information hierarchy needs adjustment.

- Keep the header, filters, KPIs, AI analysis, chart, and cost table visible by default.
- Move `Registrar Nuevo Gasto` behind a secondary `Agregar o corregir manualmente` disclosure or toggle.
- Keep row-level editing and deletion available in the table. This is the correction path for the `type` that Telegram cost ingestion auto-defaults (see [[easyTool.md]] §3), so it must stay reachable.
- Preserve the existing API requests, validation, filtering, and pagination behavior.
- Add only the minimal CSS required for the disclosure state.

### 4. Finish the styling consistency pass

Use existing design tokens and shared classes. Add CSS only when no suitable class exists.

#### `src/views/OperatorPage.jsx`

- Replace all Tailwind-style class strings with semantic classes such as:
  - `operator-page`
  - `operator-tenant-grid`
  - `operator-tenant-card`
  - `operator-tenant-card-active`
  - `operator-metric-grid`
  - `operator-action-table`
- Use the existing black, white, gray, and teal palette.
- Keep the `main-content` wrapper in `App.js`; do not add a second sidebar offset inside the page.
- Add loading, error, empty, selected, and hover states to `styles.css`.
- Preserve all API and tenant-selection behavior.

#### `src/views/dashboard.jsx`

- Remove undefined `flex` and `p-3` classes.
- Retain `dashboard-layout`, `dashboard-content`, and `w-100` only where they have defined behavior.
- Remove obsolete layout comments and whitespace placeholders.

#### `src/loader.jsx`

- Replace wrapper and heading inline styles with named classes.
- Define `.loader-spinner`, including its animation and reduced-motion behavior.

#### Remaining inline-style targets

Extract repeated/static inline styles from:

- `src/components/ordersTable.jsx`
- `src/views/invoiceDetail.js`
- `src/views/PrivacyPolicy.jsx`
- `src/views/adminPage.js`

For `adminPage.js`, preserve the existing uncommitted user changes and edit only the style-related lines needed by this plan.

Dynamic values that genuinely depend on component props may remain inline. For example, the configurable border color in `components/statCard.jsx` is not part of this cleanup unless converted to a CSS custom property without changing its API.

### 5. CSS additions and cleanup

Update `src/css/styles.css` with narrowly scoped classes for:

- Sidebar section labels and secondary navigation grouping
- Secondary manual-entry actions and disclosures
- Orders-table loading and empty states
- Invoice detail grids, forms, and empty cells
- Operator page layout and states
- Privacy policy typography and spacing
- Loader layout and spinner animation

Constraints:

- Reuse `--color-*`, `--radius-*`, and `--shadow-*` tokens.
- Do not introduce Tailwind or another styling dependency.
- Do not rename broad existing classes unless all consumers are verified.
- Do not alter the already-completed Costs/Finance utility classes without a demonstrated conflict.
- Preserve responsive behavior at the existing 1200px, 960px, and 768px breakpoints.

## Tests

Add focused React Testing Library tests rather than relying only on visual inspection.

### Routing and sidebar

- `GESTOR` sees Finanzas, Facturas, and Clientes; does not see Costos, Panel Admin, or Operador.
- `ADMIN` sees the primary links plus Costos and Panel Admin; does not see Operador.
- `SUPER_ADMIN` sees Operador and Perfil without links to client routes that reject the role.
- Root and wildcard navigation select the correct destination for each role.
- `/registro`, `/products/:productId`, and `/inventory` are no longer registered routes.

### Manual invoice entry

- The main invoice list exposes `Agregar o corregir manualmente` to editable roles.
- Filtered invoice lists do not expose invoice creation.
- Read-only roles cannot open the creation modal.
- Closing the modal refreshes the list as it does today.

### Cost entry

- Cost reporting content is visible initially.
- The manual cost form is hidden initially and appears when its disclosure is activated.

Tests should mock API calls and avoid depending on a running backend.

## Verification

1. Run the automated frontend tests in non-watch mode.
2. Run the production build:

   ```powershell
   npm.cmd run build
   ```

3. Smoke-test authenticated navigation for `GESTOR`, `ADMIN`, and `SUPER_ADMIN`.
4. Verify the following correction paths end to end:
   - Main invoice list -> manual creation modal
   - Invoice detail -> invoice edit
   - Costs -> reveal manual form
   - Costs table -> edit existing cost
5. Check desktop and responsive layouts at approximately 1200px, 960px, and 768px.
6. Confirm OperatorPage no longer depends on Tailwind-style classes and does not receive a duplicate sidebar offset.
7. Audit touched files for remaining static `style={{...}}` declarations.
8. Confirm `.loader-spinner`, `flex`, and `p-3` are no longer undefined references.

Build warnings about stale Browserslist or baseline-browser mapping data are dependency-maintenance concerns and are outside this cleanup unless they become build failures.

## Execution order

1. Routes and role navigation
2. Invoice creation demotion
3. Cost entry demotion
4. OperatorPage migration
5. Loader, dashboard, and remaining inline-style cleanup
6. Automated tests and responsive smoke testing

This ordering fixes access inconsistencies first, then changes the information hierarchy, and finally performs styling extraction against the resulting markup.

## Files expected to change

- `frontEnd/workflow/src/App.js`
- `frontEnd/workflow/src/components/sidebar.jsx`
- `frontEnd/workflow/src/components/ordersTable.jsx`
- `frontEnd/workflow/src/views/invoicesAll.js`
- `frontEnd/workflow/src/views/CostsManager.jsx`
- `frontEnd/workflow/src/views/OperatorPage.jsx`
- `frontEnd/workflow/src/views/dashboard.jsx`
- `frontEnd/workflow/src/loader.jsx`
- `frontEnd/workflow/src/views/invoiceDetail.js`
- `frontEnd/workflow/src/views/PrivacyPolicy.jsx`
- `frontEnd/workflow/src/views/adminPage.js`
- `frontEnd/workflow/src/css/styles.css`
- New frontend test files colocated with the relevant components or under `src/__tests__`

## Out of scope

- Deleting the registration backend endpoint
- Deleting `views/registro.js`
- Changing invoice or cost API contracts
- Implementing Telegram ingestion or confirm-before-save behavior
- Replacing Create React App or upgrading dependencies
- Redesigning charts, branding, or the overall visual system
- Removing manual invoice or cost correction entirely
