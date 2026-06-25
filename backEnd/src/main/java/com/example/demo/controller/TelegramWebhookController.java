package com.example.demo.controller;

import com.example.demo.config.TenantContext;
import com.example.demo.dto.LedgerIngestionResult;
import com.example.demo.model.Customer;
import com.example.demo.model.Invoice;
import com.example.demo.model.LedgerDirection;
import com.example.demo.model.PaymentReminder;
import com.example.demo.model.ReminderStatus;
import com.example.demo.model.Status;
import com.example.demo.model.TelegramConnection;
import com.example.demo.model.WorkOrder;
import com.example.demo.repository.CustomerRepo;
import com.example.demo.repository.InvoiceRepo;
import com.example.demo.repository.PaymentReminderRepo;
import com.example.demo.repository.WorkOrderRepo;
import com.example.demo.service.LedgerIngestionService;
import com.example.demo.service.TelegramConnectionService;
import com.example.demo.service.TelegramIngestionWorker;
import com.example.demo.service.TelegramService;
import com.example.demo.service.ingestion.PendingLedger;
import com.example.demo.service.ingestion.PendingLedgerStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@RestController
@RequestMapping("/api/telegram")
public class TelegramWebhookController {
    private static final Logger log = LoggerFactory.getLogger(TelegramWebhookController.class);
    private static final int OVERDUE_LIMIT = 15;
    private static final String CONNECT_INSTRUCTIONS = "Para conectar este chat, genera un codigo en Admin > Telegram y envia /connect CODIGO desde un chat privado.";
    private static final String RECONNECT_INSTRUCTIONS = "Este chat no esta conectado. Reconecta con /connect.";
    private static final String PRIVATE_ONLY = "Telegram se conecta solo desde un chat privado con el bot.";

    private final CustomerRepo customerRepo;
    private final PaymentReminderRepo reminderRepo;
    private final InvoiceRepo invoiceRepo;
    private final WorkOrderRepo workOrderRepo;
    private final TelegramService telegramService;
    private final TelegramConnectionService connectionService;
    private final String webhookSecretToken;
    private final LedgerIngestionService ingestionService;
    private final TelegramIngestionWorker ingestionWorker;
    private final PendingLedgerStore pendingLedgerStore;
    private final Executor ingestionExecutor;

    public TelegramWebhookController(CustomerRepo customerRepo,
                                     PaymentReminderRepo reminderRepo,
                                     InvoiceRepo invoiceRepo,
                                     WorkOrderRepo workOrderRepo,
                                     TelegramService telegramService,
                                     TelegramConnectionService connectionService,
                                     LedgerIngestionService ingestionService,
                                     TelegramIngestionWorker ingestionWorker,
                                     PendingLedgerStore pendingLedgerStore,
                                     @Qualifier("telegramIngestionExecutor") Executor ingestionExecutor,
                                     @Value("${telegram.webhook.secret-token:}") String webhookSecretToken) {
        this.customerRepo = customerRepo;
        this.reminderRepo = reminderRepo;
        this.invoiceRepo = invoiceRepo;
        this.workOrderRepo = workOrderRepo;
        this.telegramService = telegramService;
        this.connectionService = connectionService;
        this.ingestionService = ingestionService;
        this.ingestionWorker = ingestionWorker;
        this.pendingLedgerStore = pendingLedgerStore;
        this.ingestionExecutor = ingestionExecutor;
        this.webhookSecretToken = webhookSecretToken == null ? "" : webhookSecretToken.trim();
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
            @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String secretToken,
            @RequestBody Map<String, Object> update) {
        if (!isValidWebhookSecret(secretToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            Map<?, ?> callbackQuery = asMap(update.get("callback_query"));
            if (callbackQuery != null) {
                handleCallbackQuery(callbackQuery);
                return ResponseEntity.ok().build();
            }

            Map<?, ?> message = asMap(update.get("message"));
            if (message == null) message = asMap(update.get("edited_message"));
            if (message == null) return ResponseEntity.ok().build();

            Map<?, ?> chat = asMap(message.get("chat"));
            if (chat == null) return ResponseEntity.ok().build();

            String chatId = stringValue(chat.get("id"));
            String chatType = stringValue(chat.get("type"));
            String chatTitle = chatTitle(chat);
            String text = stringValue(message.get("text")).trim();

            if (isConnectCommand(text)) {
                handleConnectCommand(chatId, chatType, chatTitle, text);
                return ResponseEntity.ok().build();
            }

            if (!text.isBlank() && captureReminderResponse(chatId, text)) {
                return ResponseEntity.ok().build();
            }

            Optional<TelegramConnection> connection = connectionService.resolveConnection(chatId);
            if (connection.isEmpty()) {
                handleUnknownChat(chatId, chatType, message, text);
                return ResponseEntity.ok().build();
            }

            Long tenantId = connection.get().getTenant().getId();
            TenantContext.set(tenantId);
            try {
                if (isPhotoMessage(message)) {
                    handleMediaMessage(connection.get(), chatId, message, true);
                    return ResponseEntity.ok().build();
                }

                if (message.get("document") instanceof Map<?, ?>) {
                    handleMediaMessage(connection.get(), chatId, message, false);
                    return ResponseEntity.ok().build();
                }

                if (isAdminCommand(text) || text.startsWith("/")) {
                    handleAdminCommand(connection.get(), chatId, text);
                    return ResponseEntity.ok().build();
                }

                if (!text.isBlank()) {
                    submitTextIngestion(connection.get(), chatId, message, text);
                }
            } finally {
                TenantContext.clear();
            }
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Telegram webhook processing failed", e);
            return ResponseEntity.ok().build();
        }
    }

    private void handleConnectCommand(String chatId, String chatType, String chatTitle, String text) {
        try {
            String code = text.trim().split("\\s+", 2).length == 2 ? text.trim().split("\\s+", 2)[1] : "";
            connectionService.consumeConnectCode(code, chatId, chatType, chatTitle);
            telegramService.sendMessage(chatId, "Telegram conectado a FinanzasFlow.");
        } catch (Exception e) {
            telegramService.sendMessage(chatId, safeError(e.getMessage(), CONNECT_INSTRUCTIONS));
        }
    }

    private void handleUnknownChat(String chatId, String chatType, Map<?, ?> message, String text) {
        if (!"private".equalsIgnoreCase(chatType) && (isPhotoMessage(message) || message.get("document") instanceof Map<?, ?> || text.startsWith("/connect"))) {
            telegramService.sendMessage(chatId, PRIVATE_ONLY);
            return;
        }
        if (isPhotoMessage(message) || message.get("document") instanceof Map<?, ?> || !text.isBlank()) {
            telegramService.sendMessage(chatId, CONNECT_INSTRUCTIONS);
        }
    }

    private void handleMediaMessage(TelegramConnection connection, String chatId, Map<?, ?> message, boolean photo) {
        Map<?, ?> media;
        String mediaType;
        if (photo) {
            List<?> photos = (List<?>) message.get("photo");
            media = asMap(photos.get(photos.size() - 1));
            mediaType = "image/jpeg";
        } else {
            media = asMap(message.get("document"));
            mediaType = media == null ? "" : stringValue(media.get("mime_type"));
        }
        if (media == null || !Set.of("application/pdf", "image/jpeg", "image/png", "image/webp").contains(mediaType)) {
            telegramService.sendMessage(chatId, "Formato no soportado. Envia PDF, JPEG, PNG o WebP.");
            return;
        }
        String fileId = stringValue(media.get("file_id"));
        Long messageId = numberValue(message.get("message_id"));
        String caption = stringValue(message.get("caption"));
        if (fileId.isBlank() || messageId == null) return;

        Long tenantId = connection.getTenant().getId();
        pendingLedgerStore.claim(chatId, messageId, tenantId).ifPresent(token ->
                submitAsync(token, chatId, tenantId,
                        () -> ingestionWorker.processMedia(token, chatId, fileId, mediaType, caption)));
    }

    private void submitTextIngestion(TelegramConnection connection, String chatId, Map<?, ?> message, String text) {
        Long messageId = numberValue(message.get("message_id"));
        if (messageId == null) return;
        Long tenantId = connection.getTenant().getId();
        pendingLedgerStore.claim(chatId, messageId, tenantId).ifPresent(token ->
                submitAsync(token, chatId, tenantId, () -> ingestionWorker.processText(token, chatId, text)));
    }

    private void submitAsync(long token, String chatId, Long tenantId, Runnable work) {
        try {
            ingestionExecutor.execute(() -> {
                TenantContext.set(tenantId);
                try {
                    work.run();
                } finally {
                    TenantContext.clear();
                }
            });
        } catch (RejectedExecutionException e) {
            pendingLedgerStore.remove(token);
            telegramService.sendMessage(chatId, "Hay demasiadas cargas en proceso. Reenvia el mensaje en unos minutos.");
        }
    }

    private void handleCallbackQuery(Map<?, ?> callback) {
        String callbackId = stringValue(callback.get("id"));
        Map<?, ?> message = asMap(callback.get("message"));
        Map<?, ?> chat = message == null ? null : asMap(message.get("chat"));
        String chatId = chat == null ? "" : stringValue(chat.get("id"));
        answerCallbackQuery(callbackId, chatId);

        String[] parts = stringValue(callback.get("data")).split(":");
        if (parts.length != 3 || !"ledger".equals(parts[0])) return;
        Long token = parseOptionalLong(parts[1]);
        LedgerDirection direction;
        try {
            direction = LedgerDirection.valueOf(parts[2]);
        } catch (Exception e) {
            return;
        }
        if (token == null) return;

        Optional<PendingLedger> pending = pendingLedgerStore.get(token);
        if (pending.isEmpty()) {
            telegramService.sendMessage(chatId, "Esa carga expiro o ya fue procesada. Reenvia el documento para guardarlo.");
            return;
        }
        if (!pending.get().chatId().equals(chatId)) {
            log.warn("Telegram callback token={} rejected for chatId={}", token, chatId);
            telegramService.sendMessage(chatId, "Esa carga expiro o ya fue procesada. Reenvia el documento para guardarlo.");
            return;
        }

        Optional<TelegramConnection> resolvedConnection = connectionService.resolveConnection(chatId);

        resolvedConnection.ifPresentOrElse(connection -> {
            Long tenantId = connection.getTenant().getId();
            Long ownerId = connection.getDefaultOwner() == null ? null : connection.getDefaultOwner().getId();
            if (ownerId == null) {
                telegramService.sendMessage(chatId, "No pude guardar el registro: falta usuario responsable para este chat.");
                return;
            }
            TenantContext.set(tenantId);
            try {
                LedgerIngestionResult result = ingestionService.finalizeDirection(pending.get(), ownerId, direction);
                pendingLedgerStore.remove(token);
                telegramService.sendMessage(chatId, ingestionWorker.completedText(result));
            } catch (Exception e) {
                telegramService.sendMessage(chatId, "No pude guardar el registro. Revisa el documento o intenta nuevamente.");
            } finally {
                TenantContext.clear();
            }
        }, () -> {
            log.warn("Telegram callback for chatId={} token={} has no enabled connection", chatId, token);
            telegramService.sendMessage(chatId, RECONNECT_INSTRUCTIONS);
        });
    }

    private void answerCallbackQuery(String callbackId, String chatId) {
        if (callbackId.isBlank()) return;
        try {
            telegramService.answerCallbackQuery(callbackId);
        } catch (Exception e) {
            log.warn("Failed to answer Telegram callback query callbackId={} chatId={}",
                    callbackId, chatId, e);
        }
    }

    private void handleAdminCommand(TelegramConnection connection, String chatId, String text) {
        String normalized = normalizeCommand(text);
        Long tenantId = connection.getTenant().getId();
        try {
            if (normalized.equals("/start") || normalized.equals("/help") || normalized.equals("help") || normalized.equals("ayuda")) {
                telegramService.sendMessage(chatId, helpText());
                return;
            }

            if (normalized.equals("/overdue") || normalized.equals("overdue")
                    || normalized.equals("/vencidas") || normalized.equals("vencidas")
                    || normalized.equals("atrasadas")) {
                telegramService.sendMessage(chatId, overdueSummary(tenantId));
                return;
            }

            if (normalized.startsWith("/status ") || normalized.startsWith("status ")) {
                telegramService.sendMessage(chatId, updateStatus(text, tenantId));
                return;
            }

            if (normalized.startsWith("/note ") || normalized.startsWith("note ")
                    || normalized.startsWith("/nota ") || normalized.startsWith("nota ")) {
                telegramService.sendMessage(chatId, appendNote(text, tenantId));
                return;
            }

            telegramService.sendMessage(chatId, "No entendi el comando.\n\n" + helpText());
        } catch (Exception e) {
            telegramService.sendMessage(chatId, "No pude ejecutar el comando: " + e.getMessage());
        }
    }

    private String overdueSummary(Long tenantId) {
        List<Invoice> overdue = invoiceRepo.findOverdueOpenInvoicesByTenant(LocalDate.now(), tenantId);
        if (overdue.isEmpty()) {
            return "No hay facturas vencidas abiertas.";
        }

        BigDecimal total = overdue.stream()
                .map(Invoice::getPrecio)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        StringBuilder sb = new StringBuilder();
        sb.append("Facturas vencidas abiertas: ").append(overdue.size()).append("\n");
        sb.append("Total: ").append(money(total)).append("\n\n");

        overdue.stream().limit(OVERDUE_LIMIT).forEach(invoice -> {
            LocalDate dueDate = invoice.getFechaEntrega() != null ? invoice.getFechaEntrega() : invoice.getFechaEstimada();
            String customer = invoice.getCustomer() != null ? invoice.getCustomer().getName() : "Sin cliente";
            String status = invoice.getWorkOrder() != null && invoice.getWorkOrder().getStatus() != null
                    ? invoice.getWorkOrder().getStatus().name()
                    : "SIN_ESTADO";

            sb.append("#").append(invoice.getId())
                    .append(" | ").append(customer)
                    .append(" | ").append(money(invoice.getPrecio()))
                    .append(" | vence ").append(dueDate)
                    .append(" | ").append(status)
                    .append("\n");
        });

        if (overdue.size() > OVERDUE_LIMIT) {
            sb.append("\nMostrando ").append(OVERDUE_LIMIT).append(" de ").append(overdue.size()).append(".");
        }

        return sb.toString();
    }

    private String updateStatus(String text, Long tenantId) {
        String[] parts = text.trim().split("\\s+", 3);
        if (parts.length < 3) {
            return "Formato: /status <invoiceId> <EN_GESTION|CONTACTADO|PROMETIO_PAGO|EN_DISPUTA|INCOBRABLE|CERRADO>";
        }

        Long invoiceId = parseId(parts[1]);
        Status status = parseStatus(parts[2]);
        invoiceRepo.findByIdAndTenant_Id(invoiceId, tenantId)
                .orElseThrow(() -> new RuntimeException("No encontre factura #" + invoiceId));
        WorkOrder workOrder = workOrderRepo.findByInvoice_Id(invoiceId)
                .orElseThrow(() -> new RuntimeException("No encontre gestion para factura #" + invoiceId));

        workOrder.setStatus(status);
        workOrder.setUpdateAt(LocalDateTime.now());
        workOrderRepo.save(workOrder);

        return "Factura #" + invoiceId + " actualizada a " + status.name() + ".";
    }

    private String appendNote(String text, Long tenantId) {
        String[] parts = text.trim().split("\\s+", 3);
        if (parts.length < 3 || parts[2].isBlank()) {
            return "Formato: /note <invoiceId> <nota>";
        }

        Long invoiceId = parseId(parts[1]);
        Invoice invoice = invoiceRepo.findByIdAndTenant_Id(invoiceId, tenantId)
                .orElseThrow(() -> new RuntimeException("No encontre factura #" + invoiceId));

        String existing = invoice.getNotas() == null || invoice.getNotas().isBlank() ? "" : invoice.getNotas().trim();
        String note = "[Telegram " + LocalDateTime.now().withNano(0) + "] " + parts[2].trim();
        invoice.setNotas(existing.isBlank() ? note : existing + "\n" + note);
        invoiceRepo.save(invoice);

        return "Nota agregada a factura #" + invoiceId + ".";
    }

    private boolean captureReminderResponse(String chatId, String text) {
        return customerRepo.findByPhone(chatId)
                .flatMap(this::latestReminder)
                .map(reminder -> {
                    reminder.setResponse(text);
                    reminder.setRespondedAt(LocalDateTime.now());
                    reminder.setStatus(ReminderStatus.RESPONDED);
                    reminderRepo.save(reminder);
                    return true;
                }).orElse(false);
    }

    private Optional<PaymentReminder> latestReminder(Customer customer) {
        return reminderRepo.findTopByCustomer_IdOrderBySentAtDesc(customer.getId());
    }

    private boolean isAdminCommand(String text) {
        if (text == null || text.isBlank()) return false;
        String normalized = normalizeCommand(text);
        return normalized.equals("/start")
                || normalized.equals("/help")
                || normalized.equals("help")
                || normalized.equals("ayuda")
                || normalized.equals("/overdue")
                || normalized.equals("overdue")
                || normalized.equals("/vencidas")
                || normalized.equals("vencidas")
                || normalized.equals("atrasadas")
                || normalized.startsWith("/status ")
                || normalized.startsWith("status ")
                || normalized.startsWith("/note ")
                || normalized.startsWith("note ")
                || normalized.startsWith("/nota ")
                || normalized.startsWith("nota ");
    }

    private boolean isConnectCommand(String text) {
        return normalizeCommand(text).startsWith("/connect");
    }

    private boolean isValidWebhookSecret(String secretToken) {
        return !webhookSecretToken.isBlank() && webhookSecretToken.equals(secretToken);
    }

    private boolean isPhotoMessage(Map<?, ?> message) {
        Object photos = message.get("photo");
        return photos instanceof List<?> list && !list.isEmpty();
    }

    private Long parseId(String raw) {
        try {
            return Long.parseLong(raw.replace("#", ""));
        } catch (NumberFormatException e) {
            throw new RuntimeException("ID invalido: " + raw);
        }
    }

    private Status parseStatus(String raw) {
        try {
            return Status.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new RuntimeException("Estado invalido: " + raw);
        }
    }

    private String normalizeCommand(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    private String money(BigDecimal value) {
        BigDecimal safeValue = value == null ? BigDecimal.ZERO : value;
        return "$" + safeValue.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private String helpText() {
        return """
                Comandos FinanzasFlow:
                /overdue - lista facturas vencidas abiertas
                /status <invoiceId> <STATUS> - actualiza gestion
                /note <invoiceId> <nota> - agrega nota a la factura

                Estados: EN_GESTION, CONTACTADO, PROMETIO_PAGO, EN_DISPUTA, INCOBRABLE, CERRADO
                """;
    }

    private Long parseOptionalLong(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long numberValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        return parseOptionalLong(stringValue(value));
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String chatTitle(Map<?, ?> chat) {
        String title = stringValue(chat.get("title"));
        if (!title.isBlank()) return title;
        String firstName = stringValue(chat.get("first_name"));
        String lastName = stringValue(chat.get("last_name"));
        return (firstName + " " + lastName).trim();
    }

    private String safeError(String message, String fallback) {
        if (message == null || message.isBlank()) return fallback;
        String safe = message.replaceAll("[\\r\\n]+", " ").trim();
        return safe.length() > 300 ? safe.substring(0, 300) : safe;
    }

    private Map<?, ?> asMap(Object value) {
        return value instanceof Map<?, ?> map ? map : null;
    }
}
