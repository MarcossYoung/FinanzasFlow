 # Context / symptom

 Tapping Cobro (factura) / Gasto (proveedor) on a pending Telegram message shows the
 button "updating" spinner that never clears; the ingestion row stays in PENDING_DIRECTION
 and no invoice/cost is ever created. The multi-tenant connection refactor
 (telegram_connections, /connect, connectionService) is already implemented but
 uncommitted in the working tree.

 # Diagnosis (from static trace)

 The button spinner only clears when the bot calls telegramService.answerCallbackQuery(...),
 which happens only in the present-branch of handleCallbackQuery
 (TelegramWebhookController.java:257). The row only stays PENDING_DIRECTION if
 finalizeDirection() is never reached — if it were reached and threw, the row would go to
 FAILED via markFailed (:273).

 # So finalizeDirection is not being invoked. The callback can only avoid it by:
 1. connectionService.resolveConnection(chatId) returning empty -> else-branch (:278)
 sends CONNECT_INSTRUCTIONS, never finalizes, and never answers the callback (spinner
 hangs); or
 2. resolveConnection (or anything in handleCallbackQuery) throwing — the webhook wraps
 the whole dispatch in catch (Exception ignored) (:150) and returns 200, so the error is
 silently swallowed and the spinner hangs.

 Both collapse to: no usable enabled telegram_connections row for this chat at tap time,
 or a swallowed exception — and the current code makes it impossible to tell which, because the
 failure is invisible (no log, no callback answer, no message the user noticed).

 The earlier "Cuenta origen / customer" issue is a finalize-time concern; it can only matter
 once finalizeDirection is actually reached, so it is secondary to this blocker.

 # Plan

 # Step 1 — Make callback failures observable (do this first; it names the real cause)

 File: controller/TelegramWebhookController.java, method handleCallbackQuery (:236-279)
 and the webhook catch (:150).
 - Always answer the callback so the spinner clears in every branch: call
 answerCallbackQuery(callbackId) once near the top of handleCallbackQuery (after parsing
 callbackId), not only inside the present-branch.
 - In the else-branch (:278), log at WARN with the chatId and ingestionId
 ("callback for chat X has no enabled connection") and send a clearer message than the
 generic connect blurb (e.g. "Este chat no esta conectado. Reconecta con /connect.").
 - Replace the swallowing catch (Exception ignored) (:150) with catch (Exception e) that
 logs (log.warn/error) the exception before returning 200. (Keep returning 200 so
 Telegram doesn't retry-storm, but stop discarding the cause.)
 - Wrap the resolveConnection call in the callback so a thrown persistence error (e.g. missing
 V8 table) is logged distinctly from "empty".

 After this change: redeploy, tap the button again, and read the one log line — it will state
 definitively whether it's (a) empty connection, (b) a thrown exception, or (c) a
 SecurityException tenant mismatch.

 # Step 2 — Fix the connection state (most likely root cause)

 - Confirm the live chat has an enabled row: SELECT id, chat_id, tenant_id, default_owner_id, enabled FROM telegram_connections;
 and that chat_id exactly equals the
 Telegram chat id used in the pending ingestion
 (SELECT chat_id, status FROM telegram_ledger_ingestions WHERE status='PENDING_DIRECTION';).
 - If no enabled row matches: generate a code in Admin > Telegram and send /connect CODE
 from that private chat (handled at :110-113, before the connection check, so an unconnected
 chat can still connect). Then re-tap (or resend) the pending message — finalizeDirection
 now resolves and completes.
 - If the rows are pre-refactor (created under the old env-var code) and telegram_connections
 is empty: same fix — create the connection, then re-tap the existing pending rows; finalize
 only needs the ingestion row + a connection at tap time.

 # Step 3 — (Conditional) finalize-time customer/Cuenta-origen fix

 Only if Step 1's log shows finalizeDirection is reached and throwing on customer/invoice
 creation: apply the counterparty fix — extract both parties on transfer receipts and pick by
 direction (origin for COBRO, destination for GASTO) in LedgerExtraction /
 LEDGER_EXTRACTION_SYSTEM (AiService.java:44) / resolveOrCreateCustomer +
 Step 3 — (Conditional) finalize-time customer/Cuenta-origen fix                                            
                                                                                                                 
Only if Step 1's log shows finalizeDirection is reached and throwing on customer/invoice                   
creation: apply the counterparty fix — extract both parties on transfer receipts and pick by               
direction (origin for COBRO, destination for GASTO) in LedgerExtraction /                                  
LEDGER_EXTRACTION_SYSTEM (AiService.java:44) / resolveOrCreateCustomer +                                   
toCostRequest (LedgerIngestionService.java:251,292), and relax the identity-required check                 
at AiService.java:181. (Detailed in prior planning; deferred until logs justify it.)                       
                                                                                                                 
 # Verification                                                                                               
                                                                                                                 
- After Step 1, tap a pending message: the spinner must clear and a log line must appear naming            
      the branch. This alone confirms the root cause.                                                            
- After Step 2, tap a pending message in a connected chat: bot replies with the completion text,           
      the ingestion row flips to COMPLETED, and the invoice/cost appears under the right tenant.                 
- Re-run the existing TelegramWebhookControllerTest / LedgerIngestionServiceTest; add a case               
      asserting that a callback for a chat with no enabled connection still answers the callback and             
      logs (does not hang). mvn test in backEnd.                                                                 
                                                                                                                 
 # Notes                                                                                                      
                                                                                                                 
      - These changes are surgical to TelegramWebhookController plus (conditionally) the AI/ledger               
      files already modified in the working tree. No schema change.                                              
      - Broader multi-tenant routing is already built; this plan only restores the                               
      tap -> finalize path and the diagnosability that the swallow + missing callback-answer removed.            
     