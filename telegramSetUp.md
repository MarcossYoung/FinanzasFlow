# Plan: Set Up the Telegram Chat (Operational Wiring)

## Context

The FinanzasFlow backend already ships a **working** Telegram integration — it just has
never been wired to a live bot. The goal here is **operational only**: create the bot,
set the environment variables, register the webhook with Telegram, and verify the
existing admin commands + daily reminders work end-to-end on the live chat. **No code
changes.** The photo/PDF ingestion feature in `easyTool.md` is explicitly out of scope
for this pass.

### What already exists in code (verified)
- `POST /api/telegram/webhook` — public endpoint (`SecurityConfig.java` permits it, no JWT),
  protected by the `X-Telegram-Bot-Api-Secret-Token` header.
- Secret validation: rejects with 401 unless `telegram.webhook.secret-token` is set **and**
  matches the header exactly (`TelegramWebhookController.isValidWebhookSecret`).
- Admin authorization: `telegram.admin.chat-ids` (comma-separated) → `isAuthorizedAdmin`.
- Admin commands: `/start`, `/help`, `/overdue` (`/vencidas`), `/status <invoiceId> <STATUS>`,
  `/note <invoiceId> <text>`. `/status` and `/note` require `telegram.admin.tenant-id`.
- Photo messages → `handlePhotoPlaceholder()` (replies "parsing queda para la siguiente etapa").
  This is expected for this pass.
- Daily reminders: `ReminderEngine` runs on `telegram.reminders.cron` (default `0 0 9 * * *`),
  sending via `TelegramService.sendMessage()`.
- Outbound send: `TelegramService.sendMessage()` uses `telegram.bot.token`.

### Config keys (all env-var-backed in `application.properties`, currently empty)
| Property | Env var | Purpose |
|---|---|---|
| `telegram.bot.token` | `TELEGRAM_BOT_TOKEN` | Bot auth for send + getFile |
| `telegram.admin.chat-ids` | `TELEGRAM_ADMIN_CHAT_IDS` | Who may run admin commands |
| `telegram.admin.tenant-id` | `TELEGRAM_ADMIN_TENANT_ID` | Tenant scope for `/status`, `/note`, `/overdue` |
| `telegram.webhook.secret-token` | `TELEGRAM_WEBHOOK_SECRET_TOKEN` | Header Telegram must echo |
| `telegram.reminders.cron` | `TELEGRAM_REMINDERS_CRON` | Reminder schedule (optional, has default) |

> Note: `telegram.admin.ingest-owner-id` from `easyTool.md` does **not** exist yet — it's
> only needed by the future ingestion feature, not this wiring.

### Deployment facts
- Hosted on **Railway** (NIXPACKS, Spring Boot jar, `server.port=${PORT}`). Env vars are set
  in the Railway service **Variables** tab; changing them triggers a redeploy.
- The webhook needs a **public HTTPS URL** = the Railway public domain + `/api/telegram/webhook`.

---

## Steps (all run by the user; I can prep exact commands)

### 1. Create the bot and get the token
- In Telegram, message **@BotFather** → `/newbot` → choose a name + username.
- Copy the token (`123456:ABC-...`). This becomes `TELEGRAM_BOT_TOKEN`.

### 2. Get the admin chat ID
- Start a chat with the new bot and send any message (e.g. `hola`).
- Fetch updates to read your numeric chat id:
  `https://api.telegram.org/bot<TOKEN>/getUpdates`
  → `result[].message.chat.id`. This becomes `TELEGRAM_ADMIN_CHAT_IDS`
  (comma-separated if more than one admin).

### 3. Determine the admin tenant ID
- Needed so `/status`, `/note`, `/overdue` are scoped to the right client.
- Query the Neon/Postgres DB: `SELECT id, name, slug FROM tenants;` and pick the first
  client's `id`. This becomes `TELEGRAM_ADMIN_TENANT_ID`.

### 4. Generate a webhook secret
- Any random opaque string (e.g. `openssl rand -hex 32`). This becomes
  `TELEGRAM_WEBHOOK_SECRET_TOKEN`. Telegram echoes it in the
  `X-Telegram-Bot-Api-Secret-Token` header on every call; the controller rejects mismatches.

### 5. Set the env vars in Railway
- Railway → backend service → **Variables** → add `TELEGRAM_BOT_TOKEN`,
  `TELEGRAM_ADMIN_CHAT_IDS`, `TELEGRAM_ADMIN_TENANT_ID`, `TELEGRAM_WEBHOOK_SECRET_TOKEN`.
- Let it redeploy. Confirm the public domain (Railway → Settings → Networking →
  public domain), e.g. `https://<app>.up.railway.app`.

### 6. Register the webhook with Telegram
- Call `setWebhook`, passing the public URL **and** the secret token so Telegram echoes it:
  ```
  curl "https://api.telegram.org/bot<TOKEN>/setWebhook" \
    -d "url=https://<app>.up.railway.app/api/telegram/webhook" \
    -d "secret_token=<TELEGRAM_WEBHOOK_SECRET_TOKEN>"
  ```
  (Optionally `-d "allowed_updates=[\"message\"]"` for now; add `callback_query` later when
  the ingestion feature lands.)

### 7. Verify
- `getWebhookInfo` shows the URL set and `pending_update_count` draining, `last_error_*` empty:
  `https://api.telegram.org/bot<TOKEN>/getWebhookInfo`
- From the admin chat, send `/overdue` → expect the overdue-invoice summary (or "no overdue").
- Send `/help` → expect the command list.
- Send a photo → expect the placeholder reply (confirms photo branch + auth path work).
- Negative check: from a non-admin account, `/overdue` → "No autorizado para comandos admin."
- Reminders: optionally set `TELEGRAM_REMINDERS_CRON` to a near-future time to confirm a
  reminder fires, then restore the `0 0 9 * * *` default.

---

## Out of scope (this pass)
- Everything in `easyTool.md`: photo/PDF download, Claude Vision extraction, Cobro/Gasto
  buttons, tenant-explicit Invoice/Cost creation, the idempotency table + migration, async.
- Adding `telegram.admin.ingest-owner-id`.

## Notes / decisions for the user
- **Local testing** (optional): to hit the webhook from a local run, expose `localhost:8080`
  via a tunnel (e.g. `ngrok http 8080`) and `setWebhook` to the tunnel URL. Production
  (Railway) is the primary target.
- The bot token, chat id, tenant id, and Railway public URL are the only values you need to
  supply; I can assemble the exact `setWebhook` / `getWebhookInfo` commands once you have them.
