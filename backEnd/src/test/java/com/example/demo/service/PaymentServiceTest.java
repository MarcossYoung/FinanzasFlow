package com.example.demo.service;

import com.example.demo.config.TenantContext;
import com.example.demo.dto.CreatePaymentRequest;
import com.example.demo.model.Invoice;
import com.example.demo.model.OrderPayments;
import com.example.demo.model.Tenant;
import com.example.demo.repository.InvoiceRepo;
import com.example.demo.repository.PaymentRepo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentServiceTest {
    private final InvoiceRepo invoiceRepo = mock(InvoiceRepo.class);
    private final PaymentRepo paymentRepo = mock(PaymentRepo.class);
    private final PaymentService paymentService = new PaymentService(invoiceRepo, paymentRepo);

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void createPaymentDefaultsMissingFechaToToday() {
        stubInvoice();
        when(paymentRepo.save(any(OrderPayments.class))).thenAnswer(invocation -> invocation.getArgument(0));
        TenantContext.set(1L);

        OrderPayments payment = paymentService.createPayment(new CreatePaymentRequest(
                BigDecimal.TEN, "PAYMENT", 1L, null, null));

        assertEquals(LocalDate.now(), payment.getPaymentDate());
    }

    @Test
    void createPaymentParsesSlashSeparatedFecha() {
        stubInvoice();
        when(paymentRepo.save(any(OrderPayments.class))).thenAnswer(invocation -> invocation.getArgument(0));
        TenantContext.set(1L);

        OrderPayments payment = paymentService.createPayment(new CreatePaymentRequest(
                BigDecimal.TEN, "PAYMENT", 1L, "01/07/2026", null));

        assertEquals(LocalDate.of(2026, 7, 1), payment.getPaymentDate());
    }

    private void stubInvoice() {
        Tenant tenant = new Tenant();
        tenant.setId(1L);
        Invoice invoice = new Invoice();
        invoice.setId(1L);
        invoice.setTenant(tenant);
        when(invoiceRepo.findByIdAndTenant_Id(1L, 1L)).thenReturn(Optional.of(invoice));
    }
}
