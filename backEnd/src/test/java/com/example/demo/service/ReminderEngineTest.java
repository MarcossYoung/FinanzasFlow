package com.example.demo.service;

import com.example.demo.model.ScheduleStatus;
import com.example.demo.model.Tenant;
import com.example.demo.repository.InvoiceRepo;
import com.example.demo.repository.PaymentReminderRepo;
import com.example.demo.repository.PaymentScheduleRepo;
import com.example.demo.repository.TenantRepo;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReminderEngineTest {
    private final PaymentScheduleRepo scheduleRepo = mock(PaymentScheduleRepo.class);
    private final PaymentReminderRepo reminderRepo = mock(PaymentReminderRepo.class);
    private final TelegramService telegramService = mock(TelegramService.class);
    private final InvoiceRepo invoiceRepo = mock(InvoiceRepo.class);
    private final TenantRepo tenantRepo = mock(TenantRepo.class);

    @Test
    void runDailyRemindersQueriesEachTenantSeparatelyUsingTenantScopedRepoMethods() {
        Tenant tenantA = new Tenant();
        tenantA.setId(1L);
        Tenant tenantB = new Tenant();
        tenantB.setId(2L);
        when(tenantRepo.findAll()).thenReturn(List.of(tenantA, tenantB));
        when(invoiceRepo.findOverdueOpenInvoicesByTenant(any(LocalDate.class), any(Long.class)))
                .thenReturn(List.of());
        when(scheduleRepo.findByTenant_IdAndStatusAndExpectedDateBetween(
                any(Long.class), any(ScheduleStatus.class), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());

        ReminderEngine engine = new ReminderEngine(
                scheduleRepo, reminderRepo, telegramService, invoiceRepo, tenantRepo, "");
        engine.runDailyReminders();

        verify(invoiceRepo).findOverdueOpenInvoicesByTenant(any(LocalDate.class), eq(1L));
        verify(invoiceRepo).findOverdueOpenInvoicesByTenant(any(LocalDate.class), eq(2L));
        verify(scheduleRepo).findByTenant_IdAndStatusAndExpectedDateBetween(
                eq(1L), eq(ScheduleStatus.PENDIENTE), any(LocalDate.class), any(LocalDate.class));
        verify(scheduleRepo).findByTenant_IdAndStatusAndExpectedDateBetween(
                eq(2L), eq(ScheduleStatus.PENDIENTE), any(LocalDate.class), any(LocalDate.class));
        verify(scheduleRepo).findByTenant_IdAndStatusAndExpectedDateBetween(
                eq(1L), eq(ScheduleStatus.VENCIDO), any(LocalDate.class), any(LocalDate.class));
        verify(scheduleRepo).findByTenant_IdAndStatusAndExpectedDateBetween(
                eq(2L), eq(ScheduleStatus.VENCIDO), any(LocalDate.class), any(LocalDate.class));
    }
}
