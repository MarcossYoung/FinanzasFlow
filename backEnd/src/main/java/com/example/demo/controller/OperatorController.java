package com.example.demo.controller;

import com.example.demo.model.Invoice;
import com.example.demo.model.PaymentSchedule;
import com.example.demo.model.Tenant;
import com.example.demo.repository.InvoiceRepo;
import com.example.demo.repository.PaymentScheduleRepo;
import com.example.demo.repository.TenantRepo;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/operator")
public class OperatorController {
    private final TenantRepo tenantRepo;
    private final InvoiceRepo invoiceRepo;
    private final PaymentScheduleRepo scheduleRepo;

    public OperatorController(TenantRepo tenantRepo, InvoiceRepo invoiceRepo, PaymentScheduleRepo scheduleRepo) {
        this.tenantRepo = tenantRepo;
        this.invoiceRepo = invoiceRepo;
        this.scheduleRepo = scheduleRepo;
    }

    @GetMapping("/tenants")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> tenants() {
        List<Invoice> invoices = invoiceRepo.findAll();
        return ResponseEntity.ok(tenantRepo.findAll().stream()
                .map(tenant -> summary(tenant, invoices))
                .toList());
    }

    @GetMapping("/tenants/{id}/action-queue")
    public ResponseEntity<List<PaymentSchedule>> actionQueue(@PathVariable Long id) {
        return ResponseEntity.ok(scheduleRepo.findByTenant_IdAndExpectedDate(id, LocalDate.now()));
    }

    private Map<String, Object> summary(Tenant tenant, List<Invoice> invoices) {
        List<Invoice> tenantInvoices = invoices.stream()
                .filter(invoice -> invoice.getTenant() != null && tenant.getId().equals(invoice.getTenant().getId()))
                .toList();
        BigDecimal totalOwed = tenantInvoices.stream()
                .map(Invoice::getPrecio)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return Map.of(
                "id", tenant.getId(),
                "name", tenant.getName(),
                "totalInvoices", tenantInvoices.size(),
                "totalOwed", totalOwed,
                "avgDSO", 0
        );
    }
}
