package com.example.demo.service;

import com.example.demo.dto.AiUsage;
import com.example.demo.exceptions.AiSpendLimitExceededException;
import com.example.demo.model.Tenant;
import com.example.demo.model.TenantAiSpend;
import com.example.demo.repository.TenantAiSpendRepo;
import com.example.demo.repository.TenantRepo;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantAiSpendServiceTest {
    private final TenantAiSpendRepo repo = mock(TenantAiSpendRepo.class);
    private final TenantRepo tenantRepo = mock(TenantRepo.class);
    private final TenantAiSpendService service = service();

    @Test
    void costCentsFor_computesFromHaikuPricing() {
        BigDecimal result = service.costCentsFor(new AiUsage(1600, 300));

        assertEquals(new BigDecimal("0.3100"), result);
    }

    @Test
    void assertUnderLimit_passesWhenNoRowExists() {
        when(repo.findByTenant_IdAndPeriodYyyymm(eq(1L), anyString())).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.assertUnderLimit(1L));
    }

    @Test
    void assertUnderLimit_passesWhenSpendBelowLimit() {
        TenantAiSpend spend = spend(new BigDecimal("699.9999"));
        when(repo.findByTenant_IdAndPeriodYyyymm(eq(1L), anyString())).thenReturn(Optional.of(spend));

        assertDoesNotThrow(() -> service.assertUnderLimit(1L));
    }

    @Test
    void assertUnderLimit_throwsWhenSpendAtOrAboveLimit() {
        TenantAiSpend spend = spend(new BigDecimal("700.0000"));
        when(repo.findByTenant_IdAndPeriodYyyymm(eq(1L), anyString())).thenReturn(Optional.of(spend));

        assertThrows(AiSpendLimitExceededException.class, () -> service.assertUnderLimit(1L));
    }

    @Test
    void recordSpend_createsNewRowWhenNoneExists() {
        Tenant tenant = new Tenant();
        tenant.setId(1L);
        when(repo.findByTenant_IdAndPeriodYyyymm(eq(1L), anyString())).thenReturn(Optional.empty());
        when(tenantRepo.getReferenceById(1L)).thenReturn(tenant);

        service.recordSpend(1L, new BigDecimal("0.3100"));

        ArgumentCaptor<TenantAiSpend> captor = ArgumentCaptor.forClass(TenantAiSpend.class);
        verify(repo).save(captor.capture());
        assertEquals(tenant, captor.getValue().getTenant());
        assertEquals(new BigDecimal("0.3100"), captor.getValue().getSpendCents());
    }

    @Test
    void recordSpend_addsToExistingRow() {
        TenantAiSpend existing = spend(new BigDecimal("1.0000"));
        when(repo.findByTenant_IdAndPeriodYyyymm(eq(1L), anyString())).thenReturn(Optional.of(existing));

        service.recordSpend(1L, new BigDecimal("0.3100"));

        verify(repo).save(existing);
        assertEquals(new BigDecimal("1.3100"), existing.getSpendCents());
    }

    private TenantAiSpendService service() {
        TenantAiSpendService service = new TenantAiSpendService(repo, tenantRepo);
        ReflectionTestUtils.setField(service, "limitCents", 700);
        return service;
    }

    private TenantAiSpend spend(BigDecimal amount) {
        TenantAiSpend spend = new TenantAiSpend();
        spend.setSpendCents(amount);
        return spend;
    }
}
