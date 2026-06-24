package com.example.demo.service;

import com.example.demo.dto.LedgerExtraction;
import com.example.demo.dto.LedgerLineItemExtraction;
import com.example.demo.model.LedgerDirection;
import com.example.demo.model.TelegramIngestionStatus;
import com.example.demo.model.TelegramLedgerIngestion;
import com.example.demo.model.Tenant;
import com.example.demo.repository.CustomerRepo;
import com.example.demo.repository.TelegramLedgerIngestionRepo;
import com.example.demo.repository.TenantRepo;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LedgerIngestionServiceTest {
    private final TelegramLedgerIngestionRepo ingestionRepo = mock(TelegramLedgerIngestionRepo.class);
    private final InvoiceService invoiceService = mock(InvoiceService.class);
    private final CostService costService = mock(CostService.class);
    private final LedgerIngestionService service = new LedgerIngestionService(
            ingestionRepo, mock(TenantRepo.class), mock(CustomerRepo.class),
            invoiceService, costService, mock(PlatformTransactionManager.class),
            mock(ActivityLogService.class));

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
    void finalizeDirectionRejectsCallbackFromAnotherPendingMessage() {
        Tenant tenant = new Tenant();
        tenant.setId(1L);
        TelegramLedgerIngestion ingestion = new TelegramLedgerIngestion();
        ingestion.setId(7L);
        ingestion.setChatId("42");
        ingestion.setTenant(tenant);
        ingestion.setCallbackMessageId(70L);
        ingestion.setStatus(TelegramIngestionStatus.PENDING_DIRECTION);
        when(ingestionRepo.findLockedById(7L)).thenReturn(Optional.of(ingestion));

        assertThrows(SecurityException.class,
                () -> service.finalizeDirection(7L, "42", 1L, 71L, 2L, LedgerDirection.COBRO));
    }

    private LedgerExtraction extraction(BigDecimal amount, List<LedgerLineItemExtraction> lines) {
        return new LedgerExtraction("Documento", "ACME", null, null, null,
                amount, null, null, null, lines);
    }
}
