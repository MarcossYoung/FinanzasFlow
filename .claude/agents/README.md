# FinanzasFlow Dev Team

A role-based team of specialist agents scoped to FinanzasFlow. It layers on top of
the global utility primitives in `~/.claude/agents/` (`file-searcher`,
`code-editor`, `log-inspector`, `classifier`) — the specialists own a domain and
delegate mechanical work down to those primitives.

**Division of labor: Claude thinks/plans/analyzes/reviews. Codex builds.**

## Roster

| Agent | Mandate | Model | Who builds |
|---|---|---|---|
| `backend` | Spring Boot / Java in `backEnd/` — controllers, services, repos, DTOs, entities, Flyway, JWT, multi-tenant | sonnet | **Codex** (agent writes a plan `.md`) |
| `frontend` | React (CRA) UI in `frontEnd/workflow/src/` — components, views, `css/styles.css`, mobile UX polish | sonnet | **Codex** (agent writes a plan `.md`) |
| `devops` | CI/CD, Railway, Neon, branch protection, Flyway ops, Sentry. Owns `plans/docs/devops-cicd-hardening.md` | sonnet | **Codex** (agent writes a plan `.md`) |
| `ai-platform` | AI/Platform Architect — MCP tool-calling pivot, AiService evolution, multi-tenant agent access. Owns `plans/docs/ai-platform-mcp-proposal.md` | sonnet | **Codex** (agent writes a plan `.md`, but first deliverable is a discussion proposal, not a build) |
| `data-analyst` | Metrics/reporting over Neon; read-only by construction | sonnet | n/a — answers directly |
| `tech-lead` | One-shot delegation breakdown (no auto-dispatch) | sonnet | n/a |
| `/security-review` | **Existing global skill.** Mandatory GO/NO-GO gate before every merge to `main` | — | n/a |

## How to invoke

- **Whole team on a goal:** `/team <goal>` — the main session acts as lead,
  decomposes, dispatches to specialists, routes build work to Codex, and runs
  `/security-review` before any merge to `main`.
- **A single specialist:** `Use the backend agent to ...` /
  `Use the frontend agent to ...` / `Use the ai-platform agent to ...` /
  `Use the data-analyst agent to ...`.
- **Just a plan, no dispatch:** the `tech-lead` agent.

## Why the lead is a command, not an agent

Native Claude Code subagents cannot spawn other subagents. So the "lead that
delegates to the team" must be the **main session** — which is what `/team`
turns it into. The `tech-lead` agent is the non-dispatching, plan-only variant.

## Setup

- **Data Analyst live queries** need a SELECT-only Neon role exposed as
  `ANALYST_DATABASE_URL`, plus `psql` on PATH. Until both exist, the analyst runs
  in SQL-only mode (writes the query for you to run).
- **DevOps** uses the `gh` CLI (installed via winget; may need a shell restart to
  appear on PATH).

The team charter (roster, orchestration, stack reality, backlog) lives in
`plans/docs/devTeam.md`.
