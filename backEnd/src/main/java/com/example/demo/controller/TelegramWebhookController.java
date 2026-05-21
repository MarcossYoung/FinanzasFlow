package com.example.demo.controller;

import com.example.demo.model.Customer;
import com.example.demo.model.Invoice;
import com.example.demo.model.PaymentReminder;
import com.example.demo.model.ReminderStatus;
import com.example.demo.model.Status;
import com.example.demo.model.WorkOrder;
import com.example.demo.repository.CustomerRepo;
import com.example.demo.repository.InvoiceRepo;
import com.example.demo.repository.PaymentReminderRepo;
import com.example.demo.repository.WorkOrderRepo;
import com.example.demo.service.TelegramService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    public TelegramWebhookController(CustomerRepo customerRepo,
                                     PaymentReminderRepo reminderRepo,
                                     InvoiceRepo invoiceRepo,
                                     WorkOrderRepo workOrderRepo,
                                     TelegramService telegramService,
                                     @Value("${telegram.admin.chat-ids:}") String adminChatIds) {
        this.customerRepo = customerRepo;
        this.reminderRepo = reminderRepo;
        this.invoiceRepo = invoiceRepo;
        this.workOrderRepo = workOrderRepo;
        this.telegramService = telegramService;
        this.adminChatIds = parseAdminChatIds(adminChatIds);
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestBody Map<String, Object> update) {
        Map<?, ?> message = asMap(update.get("message"));
        if (message == null) message = asMap(update.get("edited_message"));
        if (message == null) return ResponseEntity.ok().build();

        Map<?, ?> chat = asMap(message.get("chat"));
        if (chat == null) return ResponseEntity.ok().build();

        String chatId = String.valueOf(chat.get("id"));
        String text = stringValue(message.get("text")).trim();

        if (isPhotoMessage(message)) {
            handlePhotoPlaceholder(chatId);
            return ResponseEntity.ok().build();
        }

        if (isAdminCommand(text)) {
            handleAdminCommand(chatId, text);
            return ResponseEntity.ok().build();
        }

        captureReminderResponse(chatId, text);
        return ResponseEntity.ok().build();
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
        List<Invoice> overdue = invoiceRepo.findOverdueOpenInvoices(LocalDate.now());
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
        Invoice invoice = invoiceRepo.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("No encontre factura #" + invoiceId));

        String existing = invoice.getNotas() == null || invoice.getNotas().isBlank() ? "" : invoice.getNotas().trim();
        String note = "[Telegram " + LocalDateTime.now().withNano(0) + "] " + parts[2].trim();
        invoice.setNotas(existing.isBlank() ? note : existing + "\n" + note);
        invoiceRepo.save(invoice);

        return "Nota agregada a factura #" + invoiceId + ".";
    }

    private void handlePhotoPlaceholder(String chatId) {
        if (isAuthorizedAdmin(chatId)) {
            telegramService.sendMessage(chatId, "Recibi la imagen. El parsing de facturas con Claude Vision queda para la siguiente etapa.");
        }
    }

    private void captureReminderResponse(String chatId, String text) {
        customerRepo.findByPhone(chatId)
                .flatMap(this::latestReminder)
                .ifPresent(reminder -> {
                    reminder.setResponse(text);
                    reminder.setRespondedAt(LocalDateTime.now());
                    reminder.setStatus(ReminderStatus.RESPONDED);
                    reminderRepo.save(reminder);
                });
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
        return adminChatIds.isEmpty() || adminChatIds.contains(chatId);
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

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private Map<?, ?> asMap(Object value) {
        return value instanceof Map<?, ?> map ? map : null;
    }
}
