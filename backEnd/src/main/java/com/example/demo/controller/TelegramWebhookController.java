package com.example.demo.controller;

import com.example.demo.model.Customer;
import com.example.demo.model.Invoice;
import com.example.demo.model.PaymentReminder;
import com.example.demo.model.ReminderStatus;
import com.example.demo.model.Status;
import com.example.demo.model.WorkOrder;
import com.example.demo.model.LedgerDirection;
import com.example.demo.model.TelegramIngestionStatus;
import com.example.demo.repository.CustomerRepo;
import com.example.demo.repository.InvoiceRepo;
import com.example.demo.repository.PaymentReminderRepo;
import com.example.demo.repository.WorkOrderRepo;
import com.example.demo.service.TelegramService;
import com.example.demo.service.LedgerIngestionService;
import com.example.demo.service.TelegramIngestionWorker;
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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@RestController
@RequestMapping("/api/telegram")
public class TelegramWebhookController {
    private static final int OVERDUE_LIMIT = 15;

    private final CustomerRepo customerRepo;
    private final PaymentReminderRepo reminderRepo;
    private final InvoiceRepo invoiceRepo;
    private final WorkOrderRepo workOrderRepo;
    private final TelegramService telegramService;
    private final Set<String> adminChatIds;
    private final String webhookSecretToken;
    private final Long adminTenantId;
    private final Long ingestOwnerId;
    private final LedgerIngestionService ingestionService;
    private final TelegramIngestionWorker ingestionWorker;
    private final Executor ingestionExecutor;

    public TelegramWebhookController(CustomerRepo customerRepo,
                                     PaymentReminderRepo reminderRepo,
                                     InvoiceRepo invoiceRepo,
                                     WorkOrderRepo workOrderRepo,
                                     TelegramService telegramService,
                                     LedgerIngestionService ingestionService,
                                     TelegramIngestionWorker ingestionWorker,
                                     @Qualifier("telegramIngestionExecutor") Executor ingestionExecutor,
                                     @Value("${telegram.admin.chat-ids:}") String adminChatIds,
                                     @Value("${telegram.webhook.secret-token:}") String webhookSecretToken,
                                     @Value("${telegram.admin.tenant-id:}") String adminTenantId,
                                     @Value("${telegram.admin.ingest-owner-id:}") String ingestOwnerId) {
        this.customerRepo = customerRepo;
        this.reminderRepo = reminderRepo;
        this.invoiceRepo = invoiceRepo;
        this.workOrderRepo = workOrderRepo;
        this.telegramService = telegramService;
        this.ingestionService = ingestionService;
        this.ingestionWorker = ingestionWorker;
        this.ingestionExecutor = ingestionExecutor;
        this.adminChatIds = parseAdminChatIds(adminChatIds);
        this.webhookSecretToken = webhookSecretToken == null ? "" : webhookSecretToken.trim();
        this.adminTenantId = parseOptionalLong(adminTenantId);
        this.ingestOwnerId = parseOptionalLong(ingestOwnerId);
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
            @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String secretToken,
            @RequestBody Map<String, Object> update) {
        if (!isValidWebhookSecret(secretToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

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

        String chatId = String.valueOf(chat.get("id"));
        String text = stringValue(message.get("text")).trim();

        if (isPhotoMessage(message)) {
            if (isAuthorizedAdmin(chatId)) {
                handleMediaMessage(chatId, message, true);
            }
            return ResponseEntity.ok().build();
        }

        if (message.get("document") instanceof Map<?, ?>) {
            if (isAuthorizedAdmin(chatId)) {
                handleMediaMessage(chatId, message, false);
            }
            return ResponseEntity.ok().build();
        }

        if (isAdminCommand(text) || text.startsWith("/")) {
            handleAdminCommand(chatId, text);
            return ResponseEntity.ok().build();
        }

        if (captureReminderResponse(chatId, text)) {
            return ResponseEntity.ok().build();
        }
        if (isAuthorizedAdmin(chatId) && !text.isBlank()) {
            submitTextIngestion(chatId, message, text);
        }
        return ResponseEntity.ok().build();
    }

    private void handleMediaMessage(String chatId, Map<?, ?> message, boolean photo) {
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

        LedgerIngestionService.ClaimResult claim = ingestionService.claim(chatId, messageId, requireAdminTenantId());
        if (claim.shouldProcess()) {
            submitAsync(claim.ingestionId(), chatId,
                    () -> ingestionWorker.processMedia(claim.ingestionId(), chatId, fileId, mediaType, caption));
        } else if (claim.status() == TelegramIngestionStatus.COMPLETED) {
            submitAsync(claim.ingestionId(), chatId,
                    () -> ingestionWorker.echoCompleted(claim.ingestionId(), chatId, requireAdminTenantId()));
        }
    }

    private void submitTextIngestion(String chatId, Map<?, ?> message, String text) {
        Long messageId = numberValue(message.get("message_id"));
        if (messageId == null) return;
        LedgerIngestionService.ClaimResult claim = ingestionService.claim(chatId, messageId, requireAdminTenantId());
        if (claim.shouldProcess()) {
            submitAsync(claim.ingestionId(), chatId,
                    () -> ingestionWorker.processText(claim.ingestionId(), chatId, text));
        } else if (claim.status() == TelegramIngestionStatus.COMPLETED) {
            submitAsync(claim.ingestionId(), chatId,
                    () -> ingestionWorker.echoCompleted(claim.ingestionId(), chatId, requireAdminTenantId()));
        }
    }

    private void submitAsync(Long ingestionId, String chatId, Runnable work) {
        try {
            ingestionExecutor.execute(work);
        } catch (RejectedExecutionException e) {
            ingestionService.markFailed(ingestionId, "Ingestion executor queue is full");
            telegramService.sendMessage(chatId, "Hay demasiadas cargas en proceso. Reenvia el mensaje en unos minutos.");
        }
    }

    private void handleCallbackQuery(Map<?, ?> callback) {
        String callbackId = stringValue(callback.get("id"));
        Map<?, ?> from = asMap(callback.get("from"));
        Map<?, ?> message = asMap(callback.get("message"));
        Map<?, ?> chat = message == null ? null : asMap(message.get("chat"));
        String senderId = from == null ? "" : stringValue(from.get("id"));
        String chatId = chat == null ? "" : stringValue(chat.get("id"));
        if (!isAuthorizedAdmin(chatId) && !isAuthorizedAdmin(senderId)) {
            return;
        }

        String[] parts = stringValue(callback.get("data")).split(":");
        if (parts.length != 3 || !"ledger".equals(parts[0])) return;
        Long ingestionId = parseOptionalLong(parts[1]);
        LedgerDirection direction;
        try {
            direction = LedgerDirection.valueOf(parts[2]);
        } catch (Exception e) {
            return;
        }
        if (ingestionId == null) return;
        if (ingestOwnerId == null) {
            telegramService.sendMessage(chatId, "No pude guardar el registro: TELEGRAM_ADMIN_INGEST_OWNER_ID no esta configurado.");
            return;
        }
        if (!callbackId.isBlank()) {
            try {
                telegramService.answerCallbackQuery(callbackId);
            } catch (Exception ignored) {
            }
        }
        try {
            var result = ingestionService.finalizeDirection(
                    ingestionId, chatId, requireAdminTenantId(), ingestOwnerId, direction);
            telegramService.sendMessage(chatId, ingestionWorker.completedText(result));
        } catch (Exception e) {
            telegramService.sendMessage(chatId, "No pude guardar el registro. Revisa el documento o intenta nuevamente.");
        }
    }

    private void handleAdminCommand(String chatId, String text) {
        if (!isAuthorizedAdmin(chatId)) {
            telegramService.sendMessage(chatId, "No autorizado para comandos admin.");
            return;
        }

        String normalized = normalizeCommand(text);
        try {
            if (normalized.equals("/start") || normalized.equals("/help") || normalized.equals("help") || normalized.equals("ayuda")) {
                telegramService.sendMessage(chatId, helpText());
                return;
            }

            if (normalized.equals("/overdue") || normalized.equals("overdue")
                    || normalized.equals("/vencidas") || normalized.equals("vencidas")
                    || normalized.equals("atrasadas")) {
                telegramService.sendMessage(chatId, overdueSummary());
                return;
            }

            if (normalized.startsWith("/status ") || normalized.startsWith("status ")) {
                telegramService.sendMessage(chatId, updateStatus(text));
                return;
            }

            if (normalized.startsWith("/note ") || normalized.startsWith("note ")
                    || normalized.startsWith("/nota ") || normalized.startsWith("nota ")) {
                telegramService.sendMessage(chatId, appendNote(text));
                return;
            }

            telegramService.sendMessage(chatId, "No entendi el comando.\n\n" + helpText());
        } catch (Exception e) {
            telegramService.sendMessage(chatId, "No pude ejecutar el comando: " + e.getMessage());
        }
    }

    private String overdueSummary() {
        Long tenantId = requireAdminTenantId();
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

    private String updateStatus(String text) {
        String[] parts = text.trim().split("\\s+", 3);
        if (parts.length < 3) {
            return "Formato: /status <invoiceId> <EN_GESTION|CONTACTADO|PROMETIO_PAGO|EN_DISPUTA|INCOBRABLE|CERRADO>";
        }

        Long invoiceId = parseId(parts[1]);
        Status status = parseStatus(parts[2]);
        invoiceRepo.findByIdAndTenant_Id(invoiceId, requireAdminTenantId())
                .orElseThrow(() -> new RuntimeException("No encontre factura #" + invoiceId));
        WorkOrder workOrder = workOrderRepo.findByInvoice_Id(invoiceId)
                .orElseThrow(() -> new RuntimeException("No encontre gestion para factura #" + invoiceId));

        workOrder.setStatus(status);
        workOrder.setUpdateAt(LocalDateTime.now());
        workOrderRepo.save(workOrder);

        return "Factura #" + invoiceId + " actualizada a " + status.name() + ".";
    }

    private String appendNote(String text) {
        String[] parts = text.trim().split("\\s+", 3);
        if (parts.length < 3 || parts[2].isBlank()) {
            return "Formato: /note <invoiceId> <nota>";
        }

        Long invoiceId = parseId(parts[1]);
        Invoice invoice = invoiceRepo.findByIdAndTenant_Id(invoiceId, requireAdminTenantId())
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

    private java.util.Optional<PaymentReminder> latestReminder(Customer customer) {
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

    private boolean isAuthorizedAdmin(String chatId) {
        return !adminChatIds.isEmpty() && adminChatIds.contains(chatId);
    }

    private boolean isValidWebhookSecret(String secretToken) {
        return !webhookSecretToken.isBlank() && webhookSecretToken.equals(secretToken);
    }

    private Long requireAdminTenantId() {
        if (adminTenantId == null) {
            throw new RuntimeException("TELEGRAM_ADMIN_TENANT_ID no configurado");
        }
        return adminTenantId;
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

    private Set<String> parseAdminChatIds(String raw) {
        if (raw == null || raw.isBlank()) return Set.of();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toSet());
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

    @SuppressWarnings("unchecked")
    private Map<?, ?> asMap(Object value) {
        return value instanceof Map<?, ?> map ? map : null;
    }
}
