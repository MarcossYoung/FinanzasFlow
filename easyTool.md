# Easy Tool Wedge — Text/Photo/PDF Ledger Ingestion via Claude

## Context

**Strategic decision:** adoption is the hardest problem for a new tool. Two routes were evaluated: an "easy tool" built around low-friction ingestion and dashboards, and direct integration with each client's existing systems. The easy tool remains the primary wedge because:

- Most of the **output** side already exists: invoice management, finance dashboards, cashflow projections, PDF export, and an AI weekly digest.
- Ingestion can scale across clients. A user forwards text, a photo, or a PDF to a Telegram chat instead of learning a new data-entry workflow.
- Per-client integrations remain a later enterprise upsell, not the initial adoption path.

**Framing correction (discovered during review):** the real gap is **ingestion-to-DB, not just image parsing.** Even Telegram *text* never persisted — `parseOrderDescription()` is only exposed through `/api/ai/parse-order` and is not wired to creation. So this build is the ingestion backbone for text, photo, and PDF alike. Building it once, correctly, is the reusable moat.

**Ledger direction is a first-class concern, not a scope cut.** The first client is a wholesale distributor: a large share of what he photographs are **supplier bills he pays (costs)**, not **invoices he issues (revenue)**. Silently booking a supplier bill as a revenue invoice would corrupt the very dashboards that are the payoff. So v1 classifies direction explicitly via a one-tap reply and persists **both** sides.

This build closes the main ingestion gap: a user sends invoice or bill text, a photo, or a PDF to the authorized Telegram bot, taps **Cobro** or **Gasto**, and gets a structured, tenant-scoped Invoice **or** Cost record without manually entering fields.

## Resolved v1 decisions

- **Text is in scope.** Authorized, non-command text messages use the same extraction, validation, direction, and persistence pipeline as media. Existing admin commands keep precedence; text identified as a customer reminder response keeps its existing behavior and is not ingested.
- **Costs have an owner.** Add nullable `owner_id` to `costos`, backfill it where a deterministic tenant owner exists, and require an owner for all new ingestion-created costs. This makes tenant/owner attribution testable on both ledger directions without breaking existing rows.
- **Supplier identity is not a separate entity in v1.** The current model has customers but no vendors. Cost ingestion stores a normalized supplier name plus description in `Costs.reason`; it does not create a Customer or claim vendor matching. A first-class Vendor model is a later schema feature.
- **Idempotency has two layers.** A unique `(chat_id, message_id)` constraint prevents duplicate source claims. Callback finalization additionally locks the ingestion row (or uses an equivalent atomic state transition) so concurrent double-taps cannot both create records.
- **The durable claim precedes asynchronous work.** The webhook performs only validation, authorization, and a short synchronous claim transaction before returning `200`; download and Claude work run afterward on a bounded executor.
- **External notifications happen after commit.** Invoice/work-order/line-item/payment creation and ingestion completion are one database transaction. The N8N webhook is best-effort after commit, so an external notification cannot describe a rolled-back invoice.

## Current state

### What already exists
- `AiService.parseOrderDescription()` extracts invoice fields from text into JSON.
- `AiService.callClaude()` calls Anthropic with plain-text message content.
- `TelegramWebhookController` receives updates, validates the webhook secret, recognizes authorized admin chats, and detects photo messages.
- `TelegramService` can send Telegram messages.
- `InvoiceService.createProduct()` creates invoice + work order + line items + optional deposit + downstream webhook.
- `CostController`/cost persistence exists and is tenant-scoped.

### Important gaps and constraints
- Telegram text/photo messages do **not** create any records today. The photo handler is a placeholder and never downloads files; documents aren't dispatched.
- `InvoiceService.createProduct()` relies on HTTP auth, `TenantContext`, and a `getFirstUser()` fallback. The public webhook has **no authenticated user**, so it cannot be called as-is without risking tenant mis-assignment.
- Invoice creation ignores request-provided `startDate`/`fechaEstimada`.
- Telegram retries webhook updates. Without idempotency, a slow download/Claude call creates duplicates.
- OCR/vision output is untrusted. Invalid/incomplete extraction must not persist silently.
- There is no mechanism today to distinguish a receivable (invoice) from a payable (cost) at ingestion.
- `Costs` currently has no owner relationship, and the repository has no Vendor model; both facts must be handled explicitly rather than hidden behind an invoice-shaped abstraction.

## Target flow

1. Telegram sends a webhook update containing authorized free-form text, a photo, or a supported document.
2. The controller validates the webhook secret and authorized chat before any paid/external work.
3. In a short transaction, the controller-facing service claims the Telegram message idempotently (`chat_id + message_id`). Only the request that creates or atomically reclaims the claim schedules work.
4. The controller **responds 200 immediately and processes asynchronously** on a bounded executor — Claude and download latency must never trigger Telegram retries mid-call.
5. For media, `TelegramService` resolves and downloads the file. Text skips this step.
6. `AiService` sends the text/image/PDF to Claude using the shared, **direction-agnostic** extraction schema.
7. A typed extraction result is normalized and validated.
8. The bot replies with the extracted summary and **two inline buttons — `Cobro (factura)` / `Gasto (proveedor)`** — and the ingestion record is stored as `PENDING_DIRECTION` holding the extraction JSON, tenant, and source metadata.
9. The user taps a button → a `callback_query` update arrives → the ingestion row is locked and resolved transactionally (idempotent under concurrent double-taps), then routed:
   - **Cobro** → tenant-explicit Invoice creation.
   - **Gasto** → tenant-explicit Cost creation.
10. Financial record creation and the transition to `COMPLETED` commit atomically; the bot then echoes the saved fields and record ID.
11. Retries (media or button) return the existing result instead of creating another record.

## Implementation

### 1. `AiService` — shared text and Vision extraction
Refactor extraction so text and media use one schema and one parser.
- Extract the JSON instructions from `parseOrderDescription()` into a shared prompt-builder; the schema is **direction-agnostic** — it captures the counterparty (name, CUIT/DNI, email, phone), amount/total, dates (issue/due), line items, and a free-text description/reason. The same fields map to either an Invoice (counterparty = customer) or a Cost (counterparty = supplier identity embedded in `reason`).
- Extract Anthropic HTTP/auth/response handling into a private `postToAnthropic(body)`; keep `callClaude()` as the text wrapper around it.
- Add `callClaudeVision(systemPrompt, fileBytes, mediaType, userText, maxTokens)`:
  - Images → content blocks `[{type:image, source:{type:base64, media_type:"image/jpeg", data:<base64>}}, {type:text, text:<caption>}]`.
  - PDFs → a `document` block with `media_type: application/pdf`, then a text block.
- Add `parseLedgerMediaFromBytes(bytes, mediaType, caption)` using the shared prompt.
- Add `parseLedgerText(text)` using that same prompt and typed parser. This is the entry point for authorized, non-command Telegram text.
- Keep markdown-fence cleanup + JSON deserialization in one place. Cap output ~500 tokens. Return a structured failure for missing config, HTTP error, invalid JSON, or empty response.
- Introduce a **typed extraction DTO** (not `Map<String,Object>`): `titulo`, `counterpartyName`, `cuitDni`, `email`, `phone`, `amount`, `issueDate`, `dueDate`, `description`, `lineItems`. `/api/ai/parse-order` can stay map-compatible if the frontend needs it.

### 2. `TelegramService` — file retrieval + interaction
- `String getFilePath(String fileId)` → `GET /bot{token}/getFile?file_id=...`; validate top-level `ok`; require `result.file_path`.
- `byte[] downloadFile(String filePath)` → `GET /file/bot{token}/{filePath}`. Accept only the path returned by `getFile` (no general arbitrary-path downloader).
- Add `sendMessageWithButtons(chatId, text, buttons)` (inline keyboard) and return the sent Telegram `message_id`, so it can be stored as `callback_message_id`. Add `answerCallbackQuery(callbackId)`.
- Encode callback data as a compact, validated value such as `ledger:{ingestionId}:{direction}` (within Telegram's 64-byte limit). On receipt, verify that the referenced ingestion belongs to the authorized chat and configured tenant; never trust callback data by itself.
- Throw clear, typed exceptions so callers can distinguish metadata, download, and API failures.

### 3. Tenant-explicit creation (Invoice **and** Cost)
Both ingestion targets need a creation path that does not depend on HTTP auth.
- Keep `createProduct(request)` for authenticated web requests.
- Add tenant-explicit entry points for trusted internal ingestion that accept `(request, tenantId, ownerId)` for **both** Invoice and Cost.
- Move invoice/line-item/work-order/deposit creation into one shared transactional method. Publish the N8N call after commit. Add the analogous tenant-explicit Cost creation.
- **Never** use `getFirstUser()` for ingestion. Resolve a **deterministic** owner from a configured `telegram.admin.ingest-owner-id` belonging to `telegram.admin.tenant-id`; if multiple users exist, the configured id removes ambiguity. Fail configuration validation if no valid owner is selected.
- Add `owner_id` to `costos` and an `owner` relationship to `Costs`. Existing cost rows may remain null after a safe best-effort backfill; ingestion-created costs must always receive the configured owner.
- Honor request-provided `startDate`/`fechaEntrega`/`fechaEstimada`; defaults only when absent. Keep customer lookups tenant-scoped.
- For costs, default `type` to a sensible bucket (e.g. `MATERIAL`/`OTHERS`) and `frequency = ONE_TIME`; the demoted CostsManager view ([[frontendCleanup.md]]) is the correction backstop for re-typing.

### 4. `LedgerIngestionService` — mapping, validation, routing, persistence
A transactional application service between the webhook and persistence (replaces the earlier invoice-only `InvoiceIngestionService`). Use separate short transactions for claim/state changes and a dedicated worker bean for asynchronous external work, so Spring transaction/async proxies are effective.
- Claim source messages synchronously using a unique insert or atomic stale reclaim. Return whether this request owns processing; only the owner is submitted to the executor.
- Accept the typed extraction, tenant ID, and Telegram source metadata; persist it as the `PENDING_DIRECTION` record after extraction succeeds.
- On the direction callback, route by choice:
  - **Cobro** → resolve/create tenant customer (order: CUIT/DNI → email → exact normalized name; **no global fallback**) → map to `InvoiceCreateRequest` → tenant-explicit Invoice creation.
  - **Gasto** → map to the Cost creation request (normalized supplier name + description → `reason`, amount, date) → tenant-explicit Cost creation. Do not create or resolve a Customer and do not imply a Vendor entity exists.
- Validation gate (both directions): require a positive total and meaningful counterparty/identity; reject malformed dates, negative values, inconsistent totals.
- Return the created summary for the echo-back.
- Add tenant-scoped customer queries for CUIT/DNI, email, and exact normalized name. Add a user query constrained by both owner ID and tenant ID.
- Finalize under a pessimistic row lock or equivalent compare-and-set. Invoice/Cost creation and `COMPLETED` state update must share one transaction; send the Telegram success echo only after commit.

### 5. `TelegramWebhookController` — media + callback dispatch
- Replace `handlePhotoPlaceholder` with a real media handler; add a `callback_query` branch.
- Gate on `isAuthorizedAdmin(chatId)` before any download/Claude/persist work.
- Dispatch authorized non-command `message.text` to ledger ingestion after preserving existing admin-command and reminder-response precedence.
- For `message.photo`, pick the highest-resolution item (last). Support `message.document` for `application/pdf`, `image/jpeg`, `image/png`, `image/webp`. Reject other types with a concise reply listing accepted formats. Use `message.caption` as optional context.
- For callbacks, authorize both `callback_query.from.id` and the callback message chat against the configured allowlist. v1 assumes authorized private admin chats; group-chat support requires a separate allowed-user configuration.
- Orchestrate only — no direct creation of customers/invoices/costs/line items.
- Catch expected failures with useful replies: download failure → resend; unreadable/insufficient → clearer image/PDF; unsupported → list formats; config failure → temporarily unavailable (no secrets leaked).

### 6. Idempotency + direction state for Telegram retries
One durable record drives both retry-safety and the two-step direction flow. Key on `chat_id + message_id` with a unique constraint.
- Fields: `id`, `chat_id`, `message_id`, `callback_message_id` (nullable), `tenant_id`, `status` (`PROCESSING`, `PENDING_DIRECTION`, `COMPLETED`, `FAILED`), `direction` (nullable: `COBRO`/`GASTO`), `extraction_json`, `record_type` (nullable: `INVOICE`/`COST`), `record_id` (nullable), `failure_reason` (nullable, sanitized), `created_at`, `updated_at`.
- Claim the message in a committed short transaction before executor submission. After extraction, store `extraction_json` and set `PENDING_DIRECTION` in another short transaction; do not keep a database transaction open during Telegram or Anthropic calls.
- Retry handling: `COMPLETED` → return the existing result; `PROCESSING`/`PENDING_DIRECTION` → no new Claude call; `FAILED` → allow explicit retry or ask to resend.
- **Stale-claim recovery:** a `PROCESSING` record older than N minutes (config) is atomically re-claimable, so a crash mid-Claude call doesn't wedge the message forever and two retries cannot both reclaim it.
- Button taps are idempotent under concurrency: lock the row before checking `PENDING_DIRECTION`; a tap on `COMPLETED` re-echoes the saved record without creating anything.
- A malformed/insufficient extraction transitions the ingestion claim to `FAILED` with a bounded sanitized reason. "Nothing saved" means no Invoice or Cost was created; retaining the operational claim is required for retry safety.
- Create the table via a Flyway migration.

### 7. Trust guard — direction confirm + validate before save
The **direction tap is itself a confirm step**, so no financial record is written until the human chooses Cobro/Gasto. The operational ingestion claim already exists for retry safety. Combined with the validation gate, blurry/unrelated inputs can't silently become zero-value records. Echo-back on save:

> Guardado como **gasto**: #184 — $14.500 — ACME — 30/06. Si algún dato está mal, editalo desde el panel.

A full field-level confirm/edit state machine (correct amount/date inline before save) remains a fast-follow.

## Security and operational behavior
- Reuse `anthropic.api.key`, `telegram.bot.token`, `telegram.admin.chat-ids`, `telegram.admin.tenant-id`, `telegram.admin.ingest-owner-id`, `telegram.webhook.secret-token`.
- Validate at startup that when Telegram ingestion is enabled, the configured tenant exists and the configured ingest owner belongs to it. Blank/partial ingestion configuration fails closed without affecting installations that intentionally leave Telegram disabled.
- **Process asynchronously from day one** behind the committed durable idempotency record. Use a named bounded executor with configurable worker/queue limits and explicit rejection handling; a rejected submission marks the claim `FAILED` and sends no paid request.
- Never log the Telegram token, Anthropic key, Base64 contents, or full media.
- Enforce a configurable download-size limit before Base64 encoding. Check Telegram metadata when available and enforce the limit again while reading the response body.
- MIME allowlist in the application; never trust the filename alone.
- Validate Telegram's secret-token header on every webhook (and authorize the `callback_query` sender too).

## Verification

### Automated tests
1. `AiService` builds correct Anthropic content blocks for JPEG and PDF.
2. Text and Vision extraction share one schema/parser; fenced/malformed output returns a controlled failure.
3. Webhook selects the largest photo and accepts only allowed MIME types.
4. Authorized non-command text uses the same extraction and direction flow; admin commands and reminder responses retain precedence.
5. Unauthorized chats/callbacks trigger no download, Claude call, or DB write.
6. Missing/invalid totals and garbage extraction create no financial record and leave a controlled `FAILED` claim.
7. **Cobro** routes to a tenant-scoped Invoice; **Gasto** routes to a tenant-scoped Cost; correct owner/tenant on both.
8. Customer matching stays inside the tenant; Gasto neither creates nor resolves a Customer/Vendor record.
9. Extracted issue/due dates are preserved.
10. Repeated delivery creates one claim and one Claude call; concurrent double button-taps finalize once.
11. A stale `PROCESSING` record is atomically re-claimable by only one worker after the timeout.
12. A persistence failure rolls back financial creation and ingestion completion together; no success message or N8N notification is sent.
13. Executor rejection leaves a controlled failure and performs no external work.

### End-to-end checks
1. Run the backend with Anthropic + Telegram config.
2. Authorized chat sends a clear **invoice** photo → tap **Cobro** → bot echoes amount/customer/due-date/ID → appears in dashboard + cashflow projection.
3. Send a **supplier bill** photo → tap **Gasto** → bot echoes a saved Cost → appears in costs/expense breakdown.
4. Send equivalent free-form text and verify the same direction-confirm flow without a download.
5. Send a PDF and verify the same flow.
6. Resend the same update → no duplicate; double-tap a button → one record.
7. Blurry/unrelated image → no Invoice/Cost saved and a failed operational claim remains. Unsupported document → bot lists accepted formats.
8. Text/media/callback from an unauthorized chat → no data and no paid API call.

## Files to modify or add
- `backEnd/.../service/AiService.java` — shared transport + Vision + typed extraction DTO
- `backEnd/.../service/TelegramService.java` — file download + inline buttons + answerCallbackQuery
- `backEnd/.../controller/TelegramWebhookController.java` — media + `callback_query` dispatch, async
- `backEnd/.../service/InvoiceService.java` — tenant-explicit Invoice creation
- `backEnd/.../service/CostService.java` (new) + `CostCreateRequest` — authenticated and tenant-explicit Cost creation
- `backEnd/.../service/LedgerIngestionService.java` (new) — extraction → route → persist
- `backEnd/.../service/TelegramIngestionWorker.java` (new) — external download/Claude work outside database transactions
- Typed extraction + line-item DTOs
- Tenant-scoped customer + user repository methods
- `Costs` owner relationship and migration for `costos.owner_id`
- Telegram ingestion entity + repository (with direction/state fields)
- Flyway migration for ownership and the ingestion/idempotency table
- Async executor config
- Unit + integration tests under `backEnd/src/test`

No frontend change is required for ingestion; the demoted manual entry/edit views ([[frontendCleanup.md]]) serve as the correction backstop.

## Out of scope
- Full field-level confirm/edit Telegram state machine (direction confirm **is** in scope; field edits are not).
- Telegram-based editing of an existing record.
- Cost `type` auto-classification beyond a sensible default (re-typed via the panel).
- A first-class Vendor entity, vendor deduplication, or supplier analytics; v1 preserves supplier identity in the Cost reason.
- Telegram group-chat authorization; v1 supports configured private admin chats.
- Weekly digest push notifications in Telegram.
- External accounting/ERP/client-system connectors.
- Storing original media as a permanent attachment.
