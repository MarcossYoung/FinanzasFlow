package com.example.demo.service;

import com.example.demo.dto.LedgerExtraction;
import com.example.demo.dto.LedgerLineItemExtraction;
import com.example.demo.repository.CustomerRepo;
import com.example.demo.repository.TelegramLedgerIngestionRepo;
import com.example.demo.repository.TenantRepo;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class LedgerIngestionServiceTest {
    private final LedgerIngestionService service = new LedgerIngestionService(
            mock(TelegramLedgerIngestionRepo.class), mock(TenantRepo.class), mock(CustomerRepo.class),
            mock(InvoiceService.class), mock(CostService.class), mock(PlatformTransactionManager.class),
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

    private LedgerExtraction extraction(BigDecimal amount, List<LedgerLineItemExtraction> lines) {
        return new LedgerExtraction("Documento", "ACME", null, null, null,
                amount, null, null, null, lines);
    }
}
