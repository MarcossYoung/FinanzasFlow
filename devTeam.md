# FinanzasFlow - CI/CD and Observability Hardening

## Context

FinanzasFlow is an ERP that touches money. It is currently deployed to Railway
with backend and frontend services, using Neon Postgres.

The current deployment process has no safety net:

- The solo developer can push straight to `main`.
- There is no CI gate.
- There is no per-PR data isolation.
- There is no production error monitoring.

This plan installs the four requested guardrails in dependency order.

## Stack Reality

This corrects the FastAPI/React assumption in the original request.

- **Backend:** Spring Boot 3.5.12, Java 21, Maven, located in `backEnd/`.
  Tests run against H2 in-memory, so no external database is needed in CI.
  The backend uses Flyway migrations and JWT auth.
- **Frontend:** React 19, Create React App using `react-scripts` 5.0.1,
  located in `frontEnd/workflow/`. It has a `package-lock.json`.
- **Repository:** `github.com/MarcossYoung/FinanzasFlow`, single `main` branch,
  no `.github/` directory, and no Sentry integration.
- **Existing tests:** 5 backend test classes:
  `BackEndContextTest`, `SecurityBoundaryTest`,
  `TelegramWebhookControllerTest`, `AiServiceTest`, and
  `LedgerIngestionServiceTest`; plus 4 frontend `.test.jsx` files.

## Decisions Locked With User

- Branch protection will require PRs and status checks.
- Required approvals will be `0`.
- The solo developer can self-merge but cannot push directly to `main`.
- Scope includes all four items, sequenced as described below.

## Sequencing Constraint

GitHub branch protection can only require status checks that have already run at
least once. CI must land and run before the branch-protection rule can require
those checks.

The execution order below respects that constraint.

> **Note:** The `gh` CLI is not installed in this environment, and branch
> protection, Neon, and Sentry account setup happen in external dashboards. This
> plan writes every in-repository file possible and gives exact dashboard steps
> for the rest.

---

## Step 1 - GitHub Actions CI

Create `.github/workflows/ci.yml` with two independent jobs, triggered on:

- `pull_request` to `main`
- `push` to `main`

### Backend Test Job

- Use `actions/checkout`.
- Use `actions/setup-java@v4`.
- Configure Java:
  - Distribution: `temurin`
  - Version: `21`
  - Cache: `maven`
- Run `chmod +x ./mvnw`.
- Run `./mvnw -B clean verify` with `working-directory: backEnd`.
- Provide dummy environment values so the Spring context boots.
  `JWT_SECRET` is mandatory because of prior security hardening. Add any other
  required secrets as placeholder values.
- No database service is needed because tests use H2.

### Frontend Test Job

- Use `actions/setup-node@v4`.
- Configure Node:
  - Version: `20`
  - Cache: `npm`
  - Cache dependency path: `frontEnd/workflow/package-lock.json`
- Use `working-directory: frontEnd/workflow`.
- Run `npm ci`.
- Run `CI=true npm test -- --watchAll=false`.
- Run `npm run build`.

### Bonus Quick Win

The root `.gitignore` currently ignores only `plans/` and `backEnd/target/`.
Add root `.env` and `.env.*` patterns so secrets cannot be committed. The
frontend already ignores its own environment files.

### Verification

Push a branch with this workflow, open a PR, and confirm both jobs go green.
This registers the `backend-test` and `frontend-test` check names for Step 2.

---

## Step 2 - Branch Protection on `main`

Configure this in the GitHub web UI because `gh` is not installed.

Go to:

```text
Settings -> Branches -> Add branch protection rule
```

Alternatively, use GitHub Rulesets.

Configure the rule for `main`:

- Require a pull request before merging.
- Set required approvals to `0`.
- Require status checks to pass before merging.
- Add `backend-test` and `frontend-test`.
- Require branches to be up to date before merging.
- Block force pushes.
- Restrict branch deletions.
- Leave "Include administrators" off for now, so you can bypass protection if
  the workflow itself needs fixing on `main`.

### Optional Repeatable Alternative

After installing GitHub CLI with `winget install GitHub.cli` and running
`gh auth login`, the same configuration can be applied with `gh api -X PUT` to:

```text
repos/MarcossYoung/FinanzasFlow/branches/main/protection
```

For this one-time setup, the GitHub web UI is the recommended path.

### Verification

- Attempt a direct `git push origin main`; it should be rejected.
- Open a PR with a failing check; the merge button should be blocked.

---

## Step 3 - Neon Preview Branch per PR

In the Neon dashboard:

```text
Neon Console -> Project -> Integrations -> GitHub
```

Connect `MarcossYoung/FinanzasFlow`.

Then:

- Create a Neon API key.
- Note the `NEON_PROJECT_ID`.
- Add GitHub repository secrets:
  - `NEON_API_KEY`
  - `NEON_PROJECT_ID`

Create `.github/workflows/neon-preview.yml`.

On `pull_request` events `opened`, `reopened`, and `synchronize`:

- Use `neondatabase/create-branch-action`.
- Create branch `preview/pr-${{ github.event.number }}` from `main`.
- Output its connection string.

On `pull_request` event `closed`:

- Use `neondatabase/delete-branch-action`.
- Tear down the preview branch.

This gives each PR an isolated copy of the production schema and data.

Consuming that connection string in a live Railway preview deploy is a
Railway-side step:

- Enable PR environments.
- Point the preview service `DATABASE_URL` at the Neon branch.

This is a follow-up and does not block database isolation itself.

---

## Step 4 - Sentry

Create two projects at `sentry.io`:

- `java-spring-boot`
- `react`

Copy each DSN.

### Backend

Files:

- `backEnd/pom.xml`
- `backEnd/src/main/resources/application.properties`

Changes:

- Add dependency `io.sentry:sentry-spring-boot-starter-jakarta`.
- Use the Jakarta variant because the backend is on Spring Boot 3.
- Add the following properties:

```properties
sentry.dsn=${SENTRY_DSN:}
sentry.environment=${SENTRY_ENVIRONMENT:production}
sentry.traces-sample-rate=0.2
```

An empty DSN in local and test environments means Sentry no-ops.

Set `SENTRY_DSN` in the Railway backend service.

### Frontend

Files:

- `frontEnd/workflow/src/index.js`
- `frontEnd/workflow/package.json`

Changes:

- Add `@sentry/react`.
- In `index.js`, before `ReactDOM.createRoot`, initialize Sentry:

```javascript
Sentry.init({
  dsn: process.env.REACT_APP_SENTRY_DSN,
  integrations: [Sentry.browserTracingIntegration()],
  tracesSampleRate: 0.2,
  environment: process.env.NODE_ENV,
});
```

The initialization is a no-op when `REACT_APP_SENTRY_DSN` is unset, so local
development and CI are unaffected.

Set `REACT_APP_SENTRY_DSN` in the Railway frontend build environment.

---

## Execution Order

1. Add `ci.yml` and update `.gitignore`.
2. Open a PR and confirm both CI jobs go green.
3. Enable branch protection requiring `backend-test` and `frontend-test`.
4. Configure Neon GitHub integration and repository secrets.
5. Add `neon-preview.yml`.
6. Configure Sentry SDKs for backend and frontend.
7. Create Sentry projects and set DSN environment variables in Railway.

Steps 3 and 4 are independent of each other and can be done in either order
after branch protection is configured.

---

## Risks and Verification

- **Backend context boot in CI:** `SecurityBoundaryTest` loads the full Spring
  context, and `JWT_SECRET` is mandatory. The `backend-test` job must supply
  dummy values for every environment variable the context requires, at minimum
  `JWT_SECRET`.
- **Required environment variables:** Confirm the exact required set by running
  `./mvnw test` with a clean environment locally, then add each required value
  as a placeholder `env:` entry in the workflow.
- **CRA test hang:** Use `CI=true` and `--watchAll=false`; otherwise
  `frontend-test` may never exit.
- **Solo-developer merges:** With `0` required approvals, the developer can
  self-merge PRs. Direct pushes to `main` are still blocked. This is the
  intended gate.
- **Secrets to create:**
  - GitHub repository secrets: `NEON_API_KEY`, `NEON_PROJECT_ID`
  - Railway backend env: `SENTRY_DSN`
  - Railway frontend env: `REACT_APP_SENTRY_DSN`

## Files Touched

New files:

- `.github/workflows/ci.yml`
- `.github/workflows/neon-preview.yml`

Edited files:

- `.gitignore`
- `backEnd/pom.xml`
- `backEnd/src/main/resources/application.properties`
- `frontEnd/workflow/package.json`
- `frontEnd/workflow/src/index.js`

Dashboard-only changes:

- GitHub branch protection
- Neon GitHub integration
- Sentry projects and DSNs
- Railway environment variables
