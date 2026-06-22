package com.example.demo.service;

import com.example.demo.config.TenantContext;
import com.example.demo.dto.FinanceDashboardResponse;
import com.example.demo.model.Costs;
import com.example.demo.model.Invoice;
import com.example.demo.model.OrderPayments;
import com.example.demo.repository.CostRepo;
import com.example.demo.repository.PaymentRepo;
import com.example.demo.repository.InvoiceRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FinanceService {

    private final InvoiceRepo InvoiceRepository;
    private final PaymentRepo paymentRepository;
    private final CostRepo costsRepository;

    public FinanceService(InvoiceRepo InvoiceRepository, PaymentRepo paymentRepository, CostRepo costsRepository) {
        this.InvoiceRepository = InvoiceRepository;
        this.paymentRepository = paymentRepository;
        this.costsRepository = costsRepository;
    }

    @Transactional(readOnly = true)
    public FinanceDashboardResponse dashboard(LocalDate from, LocalDate to) {
        Long tenantId = currentTenantId();
        List<Invoice> invoices = InvoiceRepository.findByStartDateBetweenAndTenant_Id(from, to, tenantId);
        List<OrderPayments> payments = paymentRepository.findByPaymentDateBetweenAndTenant_Id(from, to, tenantId);
        List<Costs> allCosts = costsRepository.findByDateBetweenAndTenant_Id(from, to, tenantId);

        Map<String, BigDecimal> breakdownMap = allCosts.stream()
                .filter(c -> c.getCostType() != null)
                .collect(Collectors.groupingBy(
                        c -> c.getCostType().name(),
                        Collectors.mapping(Costs::getAmount, Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))
                ));

        List<Map<String, Object>> expenseBreakdown = new ArrayList<>();
        breakdownMap.forEach((name, value) -> {
            Map<String, Object> item = new HashMap<>();
            item.put("name", name);
            item.put("value", nz(value));
            expenseBreakdown.add(item);
        });

        List<Map<String, Object>> userStats = getMonthlyUserStats(invoices);
        List<Map<String, Object>> customerStats = getMonthlyCustomerStats(invoices);

        BigDecimal tInc = invoices.stream()
                .map(Invoice::getPrecio)
                .map(FinanceService::nz)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tExp = allCosts.stream()
                .map(Costs::getAmount)
                .map(FinanceService::nz)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tDep = payments.stream()
                .map(OrderPayments::getAmount)
                .map(FinanceService::nz)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tCogs = BigDecimal.ZERO;
        BigDecimal grossProfit = tInc;
        BigDecimal netProfit = tInc.subtract(tExp);

        return new FinanceDashboardResponse(
                from,
                to,
                tInc,
                tDep,
                tExp,
                tInc.subtract(tExp),  // tRev kept for backward compat
                expenseBreakdown,
                userStats,
                tCogs,
                grossProfit,
                netProfit,
                customerStats
        );
    }

    public List<Map<String, Object>> getMonthlyUserStats(LocalDate from, LocalDate to) {
        return getMonthlyUserStats(InvoiceRepository.findByStartDateBetweenAndTenant_Id(from, to, currentTenantId()));
    }

    private Long currentTenantId() {
        Long tenantId = TenantContext.get();
        if (tenantId == null) {
            throw new RuntimeException("Tenant not available");
        }
        return tenantId;
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    private List<Map<String, Object>> getMonthlyUserStats(List<Invoice> invoices) {
        Map<String, List<Invoice>> byUser = invoices.stream()
                .filter(i -> i.getOwner() != null)
                .collect(Collectors.groupingBy(i -> i.getOwner().getUsername()));

        return byUser.entrySet().stream()
                .map(entry -> {
                    BigDecimal income = entry.getValue().stream()
                            .map(Invoice::getPrecio)
                            .map(FinanceService::nz)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    long unitsSold = entry.getValue().stream()
                            .mapToLong(Invoice::getCantidad)
                            .sum();

                    Map<String, Object> row = new HashMap<>();
                    row.put("userName", entry.getKey());
                    row.put("label", entry.getKey());
                    row.put("income", income);
                    row.put("unitsSold", unitsSold);
                    return row;
                })
                .toList();
    }

    private List<Map<String, Object>> getMonthlyCustomerStats(List<Invoice> invoices) {
        Map<String, List<Invoice>> byCustomer = invoices.stream()
                .collect(Collectors.groupingBy(invoice ->
                        invoice.getCustomer() != null ? invoice.getCustomer().getName() : "Sin cliente"));

        return byCustomer.entrySet().stream()
                .map(entry -> {
                    BigDecimal income = entry.getValue().stream()
                            .map(Invoice::getPrecio)
                            .map(FinanceService::nz)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    long itemCount = entry.getValue().stream()
                            .mapToLong(invoice -> invoice.getLineItems() == null ? 0 : invoice.getLineItems().size())
                            .sum();

                    Map<String, Object> row = new HashMap<>();
                    row.put("label", entry.getKey());
                    row.put("income", income);
                    row.put("itemCount", itemCount);
                    return row;
                })
                .sorted((a, b) -> ((BigDecimal) b.get("income")).compareTo((BigDecimal) a.get("income")))
                .toList();
    }
}
