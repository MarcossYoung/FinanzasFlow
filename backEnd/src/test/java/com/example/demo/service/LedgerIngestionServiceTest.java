package com.example.demo.service;

import com.example.demo.dto.LedgerExtraction;
import com.example.demo.dto.LedgerIngestionResult;
import com.example.demo.dto.LedgerLineItemExtraction;
import com.example.demo.model.LedgerDirection;
import com.example.demo.model.LedgerRecordType;
import com.example.demo.model.Costs;
import com.example.demo.repository.CustomerRepo;
import com.example.demo.repository.TenantRepo;
import com.example.demo.service.ingestion.PendingLedger;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LedgerIngestionServiceTest {
    private final TenantRepo tenantRepo = mock(TenantRepo.class);
    private final CustomerRepo customerRepo = mock(CustomerRepo.class);
    private final InvoiceService invoiceService = mock(InvoiceService.class);
    private final CostService costService = mock(CostService.class);
    private final ActivityLogService activityLogService = mock(ActivityLogService.class);
    private final LedgerIngestionService service = new LedgerIngestionService(
            tenantRepo, customerRepo, invoiceService, costService, activityLogService);

    @Test
    void acceptsPositiveConsistentExtraction() {
        LedgerExtraction extraction = extraction(new BigDecimal("20.00"), List.of(
                new LedgerLineItemExtraction("Item", new BigDecimal("2"), new BigDecimal("10"))));
        assertDoesNotThrow(() -> service.validateExtraction(extraction));
    }

    @Test
    void rejectsMissingIdentityAndInvalidTotals() {
        LedgerExtraction noIdentity = new LedgerExtraction(
                null, null, null, null, null, BigDecimal.TEN, null, null, null, List.of());
        assertThrows(IllegalArgumentException.class, () -> service.validateExtraction(noIdentity));

        LedgerExtraction mismatch = extraction(new BigDecimal("21.00"), List.of(
                new LedgerLineItemExtraction("Item", new BigDecimal("2"), new BigDecimal("10"))));
        assertThrows(IllegalArgumentException.class, () -> service.validateExtraction(mismatch));
    }

    @Test
    void normalizesSafeValuesAndDropsEmptyRows() {
        String longTitle = "x".repeat(300);
        LedgerExtraction extraction = new LedgerExtraction(longTitle, "  ACME  ", null, null, null,
                new BigDecimal("20.004"), null, null, "  nota  ", List.of(
                new LedgerLineItemExtraction("   ", new BigDecimal("-1"), new BigDecimal("-5")),
                new LedgerLineItemExtraction(" Servicio ", new BigDecimal("2.0004"), new BigDecimal("10.001"))
        ));

        LedgerExtraction normalized = service.normalizeExtraction(extraction);

        assertEquals(255, normalized.titulo().length());
        assertEquals("ACME", normalized.counterpartyName());
        assertEquals(new BigDecimal("20.00"), normalized.amount());
        assertEquals("nota", normalized.description());
        assertEquals(1, normalized.lineItems().size());
        assertEquals("Servicio", normalized.lineItems().get(0).description());
        assertEquals(new BigDecimal("2.000"), normalized.lineItems().get(0).quantity());
        assertEquals(new BigDecimal("10.00"), normalized.lineItems().get(0).unitPrice());
    }

    @Test
    void rejectsUnsafeAmountsAndLineItems() {
        assertThrows(IllegalArgumentException.class,
                () -> service.validateExtraction(extraction(new BigDecimal("10000000000.00"), List.of())));

        LedgerExtraction negativeLine = extraction(new BigDecimal("20.00"), List.of(
                new LedgerLineItemExtraction("Item", new BigDecimal("-2"), new BigDecimal("10"))));
        assertThrows(IllegalArgumentException.class, () -> service.validateExtraction(negativeLine));

        LedgerExtraction missingQuantity = extraction(new BigDecimal("20.00"), List.of(
                new LedgerLineItemExtraction("Item", null, new BigDecimal("10"))));
        assertThrows(IllegalArgumentException.class, () -> service.validateExtraction(missingQuantity));
    }

    @Test
    void finalizeDirectionCreatesCostFromPendingLedger() {
        LedgerExtraction extraction = new LedgerExtraction("Documento", "Proveedor", null, null, null,
                new BigDecimal("20.00"), LocalDate.of(2026, 6, 24), null, "Mercaderia", List.of());
        PendingLedger pending = new PendingLedger(7L, "42", 9L, 1L, extraction, Instant.now());
        Costs cost = new Costs();
        cost.setId(11L);
        when(costService.createForTenant(any(), eq(1L), eq(2L))).thenReturn(cost);

        LedgerIngestionResult result = service.finalizeDirection(pending, 2L, LedgerDirection.GASTO);

        assertEquals(7L, result.ingestionId());
        assertEquals(LedgerDirection.GASTO, result.direction());
        assertEquals(LedgerRecordType.COST, result.recordType());
        assertEquals(11L, result.recordId());
        assertEquals(new BigDecimal("20.00"), result.amount());
        assertEquals("Proveedor", result.counterparty());
        assertEquals(LocalDate.of(2026, 6, 24), result.date());
    }

    @Test
    void previewDirectionBuildsRequestWithoutPersistence() {
        LedgerExtraction extraction = new LedgerExtraction("Factura", "Cliente", "20-1", "c@example.com", "123",
                new BigDecimal("20.00"), LocalDate.of(2026, 6, 24), LocalDate.of(2026, 7, 1),
                "Servicio", List.of());
        PendingLedger pending = new PendingLedger(7L, "42", 9L, 1L, extraction, Instant.now());

        String preview = service.previewDirection(pending, LedgerDirection.COBRO);

        assertTrue(preview.contains("InvoiceCreateRequest"));
        assertTrue(preview.contains("titulo=Factura"));
        assertTrue(preview.contains("customerId=null"));
        verifyNoInteractions(tenantRepo, customerRepo, invoiceService, costService, activityLogService);
    }

    private LedgerExtraction extraction(BigDecimal amount, List<LedgerLineItemExtraction> lines) {
        return new LedgerExtraction("Documento", "ACME", null, null, null,
                amount, null, null, null, lines);
    }
}
