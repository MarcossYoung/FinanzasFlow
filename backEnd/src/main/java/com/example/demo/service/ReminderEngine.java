package com.example.demo.service;

import com.example.demo.model.Customer;
import com.example.demo.model.Invoice;
import com.example.demo.model.OrderPayments;
import com.example.demo.model.PaymentReminder;
import com.example.demo.model.PaymentSchedule;
import com.example.demo.model.ReminderChannel;
import com.example.demo.model.ReminderStatus;
import com.example.demo.model.ScheduleStatus;
import com.example.demo.model.Tenant;
import com.example.demo.repository.InvoiceRepo;
import com.example.demo.repository.PaymentReminderRepo;
import com.example.demo.repository.PaymentScheduleRepo;
import com.example.demo.repository.TenantRepo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ReminderEngine {
    private final PaymentScheduleRepo scheduleRepo;
    private final PaymentReminderRepo reminderRepo;
    private final TelegramService telegramService;
    private final InvoiceRepo invoiceRepo;
    private final TenantRepo tenantRepo;
    private final Set<String> adminChatIds;

    public ReminderEngine(PaymentScheduleRepo scheduleRepo,
                          PaymentReminderRepo reminderRepo,
                          TelegramService telegramService,
                          InvoiceRepo invoiceRepo,
                          TenantRepo tenantRepo,
                          @Value("${telegram.admin.chat-ids:}") String adminChatIds) {
        this.scheduleRepo = scheduleRepo;
        this.reminderRepo = reminderRepo;
        this.telegramService = telegramService;
        this.invoiceRepo = invoiceRepo;
        this.tenantRepo = tenantRepo;
        this.adminChatIds = parseAdminChatIds(adminChatIds);
    }

    @Scheduled(cron = "${telegram.reminders.cron:0 0 9 * * *}")
    @Transactional
    public void runDailyReminders() {
        LocalDate today = LocalDate.now();
        for (Tenant tenant : tenantRepo.findAll()) {
            connectOverdueInvoices(today, tenant.getId());
            sendScheduleReminders(today, tenant.getId());
        }
    }

    private void connectOverdueInvoices(LocalDate today, Long tenantId) {
        for (Invoice invoice : invoiceRepo.findOverdueOpenInvoicesByTenant(today, tenantId)) {
            if (invoice.getCustomer() == null || invoice.getTenant() == null) {
                sendAdminPrompt(invoice, "Factura vencida sin cliente o tenant asignado.");
                continue;
            }

            List<PaymentSchedule> openSchedules = scheduleRepo.findOpenByInvoiceId(
                    invoice.getId(),
                    List.of(ScheduleStatus.PENDIENTE, ScheduleStatus.VENCIDO)
            );
            if (!openSchedules.isEmpty()) continue;

            BigDecimal outstanding = outstandingAmount(invoice);
            if (outstanding.compareTo(BigDecimal.ZERO) <= 0) continue;

            PaymentSchedule schedule = new PaymentSchedule();
            schedule.setInvoice(invoice);
            schedule.setTenant(invoice.getTenant());
            schedule.setExpectedDate(dueDate(invoice, today));
            schedule.setAmount(outstanding);
            schedule.setStatus(ScheduleStatus.VENCIDO);
            scheduleRepo.save(schedule);
        }
    }

    private void sendScheduleReminders(LocalDate today, Long tenantId) {
        LocalDateTime dayStart = today.atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);

        for (PaymentSchedule schedule : scheduleRepo.findByTenant_IdAndStatusAndExpectedDateBetween(
                tenantId, ScheduleStatus.PENDIENTE, today.minusDays(30), today.plusDays(3))) {
            sendReminderForSchedule(schedule, today, dayStart, dayEnd);
        }

        for (PaymentSchedule schedule : scheduleRepo.findByTenant_IdAndStatusAndExpectedDateBetween(
                tenantId, ScheduleStatus.VENCIDO, today.minusDays(365), today)) {
            sendReminderForSchedule(schedule, today, dayStart, dayEnd);
        }
    }

    private void sendReminderForSchedule(PaymentSchedule schedule,
                                         LocalDate today,
                                         LocalDateTime dayStart,
                                         LocalDateTime dayEnd) {
        if (reminderRepo.existsBySchedule_IdAndSentAtBetween(schedule.getId(), dayStart, dayEnd)) return;
        if (schedule.getInvoice() == null || schedule.getInvoice().getCustomer() == null) {
            if (schedule.getInvoice() != null) {
                sendAdminPrompt(schedule.getInvoice(), "Factura con vencimiento pendiente sin cliente asignado.");
            }
            return;
        }

        Customer customer = schedule.getInvoice().getCustomer();
        boolean overdue = !schedule.getExpectedDate().isAfter(today);
        String message = customerMessage(schedule, customer, overdue);
        boolean sentToCustomer = telegramService.sendMessage(customer.getPhone(), message);

        PaymentReminder reminder = new PaymentReminder();
        reminder.setSchedule(schedule);
        reminder.setCustomer(customer);
        reminder.setTenant(schedule.getTenant());
        reminder.setChannel(ReminderChannel.TELEGRAM);
        reminder.setSentAt(LocalDateTime.now());
        reminder.setMessage(message);
        reminder.setStatus(sentToCustomer ? ReminderStatus.SENT : ReminderStatus.FAILED);
        reminderRepo.save(reminder);

        if (!sentToCustomer) {
            sendAdminPrompt(schedule.getInvoice(), "No se pudo enviar Telegram al cliente. Revisar chat/telefono: " + safe(customer.getPhone()));
        }

        if (schedule.getExpectedDate().isBefore(today) && schedule.getStatus() != ScheduleStatus.VENCIDO) {
            schedule.setStatus(ScheduleStatus.VENCIDO);
            scheduleRepo.save(schedule);
        }
    }

    private void sendAdminPrompt(Invoice invoice, String reason) {
        if (adminChatIds.isEmpty()) return;

        String customer = invoice.getCustomer() != null ? invoice.getCustomer().getName() : "Sin cliente";
        String due = dueDate(invoice, LocalDate.now()).toString();
        String message = "Accion requerida FinanzasFlow\n"
                + reason + "\n"
                + "Factura #" + invoice.getId() + " - " + safe(invoice.getTitulo()) + "\n"
                + "Cliente: " + customer + "\n"
                + "Vencimiento: " + due + "\n"
                + "Saldo estimado: " + money(outstandingAmount(invoice)) + "\n\n"
                + "Comandos: /status " + invoice.getId() + " CONTACTADO | /note " + invoice.getId() + " ...";

        for (String adminChatId : adminChatIds) {
            telegramService.sendMessage(adminChatId, message);
        }
    }

    private String customerMessage(PaymentSchedule schedule, Customer customer, boolean overdue) {
        String prefix = overdue ? "Recordatorio de pago vencido" : "Recordatorio de pago";
        return prefix + " para " + safe(customer.getName()) + ". "
                + "Factura #" + schedule.getInvoice().getId()
                + ", vence " + schedule.getExpectedDate()
                + ", saldo " + money(schedule.getAmount()) + ".";
    }

    private BigDecimal outstandingAmount(Invoice invoice) {
        BigDecimal total = invoice.getPrecio() == null ? BigDecimal.ZERO : invoice.getPrecio();
        BigDecimal paid = BigDecimal.ZERO;
        if (invoice.getOrderPayments() != null) {
            for (OrderPayments payment : invoice.getOrderPayments()) {
                if (payment.getAmount() != null) paid = paid.add(payment.getAmount());
            }
        }
        return total.subtract(paid).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private LocalDate dueDate(Invoice invoice, LocalDate fallback) {
        if (invoice.getFechaEntrega() != null) return invoice.getFechaEntrega();
        if (invoice.getFechaEstimada() != null) return invoice.getFechaEstimada();
        return fallback;
    }

    private String money(BigDecimal value) {
        BigDecimal safeValue = value == null ? BigDecimal.ZERO : value;
        return "$" + safeValue.setScale(2, RoundingMode.HALF_UP);
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private Set<String> parseAdminChatIds(String raw) {
        if (raw == null || raw.isBlank()) return Set.of();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toSet());
    }
}
