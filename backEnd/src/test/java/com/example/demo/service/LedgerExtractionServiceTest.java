package com.example.demo.service;

import com.example.demo.config.TenantContext;
import com.example.demo.dto.AiUsage;
import com.example.demo.dto.LedgerExtraction;
import com.example.demo.dto.LedgerExtractionResult;
import com.example.demo.exceptions.AiServiceException;
import com.example.demo.exceptions.AiSpendLimitExceededException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

class LedgerExtractionServiceTest {
    private final AiService aiService = mock(AiService.class);
    private final TenantAiSpendService tenantAiSpendService = mock(TenantAiSpendService.class);
    private final LedgerExtractionService service = new LedgerExtractionService(aiService, tenantAiSpendService);

    @BeforeEach
    void configureDefaults() {
        ReflectionTestUtils.setField(service, "maxFileBytes", 10485760L);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void extractsSuccessfullyForValidImage() {
        TenantContext.set(1L);
        LedgerExtraction extraction = sampleExtraction();
        AiUsage usage = new AiUsage(1600, 300);
        MockMultipartFile file = new MockMultipartFile("file", "factura.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(aiService.parseLedgerMediaFromBytesWithUsage(any(), eq("image/jpeg"), eq("factura")))
                .thenReturn(new LedgerExtractionResult(extraction, usage));
        when(tenantAiSpendService.costCentsFor(usage)).thenReturn(new BigDecimal("0.3100"));

        LedgerExtraction result = service.extract(file, "factura");

        assertSame(extraction, result);
        verify(tenantAiSpendService).assertUnderLimit(1L);
        verify(tenantAiSpendService).recordSpend(1L, new BigDecimal("0.3100"));
    }

    @Test
    void rejectsUnsupportedMediaType() {
        MockMultipartFile file = new MockMultipartFile("file", "bad.txt", "text/plain", new byte[]{1});

        assertThrows(IllegalArgumentException.class, () -> service.extract(file, null));

        verifyNoInteractions(aiService, tenantAiSpendService);
    }

    @Test
    void rejectsOversizedFile() {
        ReflectionTestUtils.setField(service, "maxFileBytes", 10L);
        MockMultipartFile file = new MockMultipartFile("file", "factura.jpg", "image/jpeg", new byte[11]);

        assertThrows(IllegalArgumentException.class, () -> service.extract(file, null));

        verifyNoInteractions(aiService, tenantAiSpendService);
    }

    @Test
    void rejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "factura.jpg", "image/jpeg", new byte[0]);

        assertThrows(IllegalArgumentException.class, () -> service.extract(file, null));

        verifyNoInteractions(aiService, tenantAiSpendService);
    }

    @Test
    void rejectsWhenSpendLimitExceeded() {
        TenantContext.set(1L);
        MockMultipartFile file = new MockMultipartFile("file", "factura.jpg", "image/jpeg", new byte[]{1});
        doThrow(new AiSpendLimitExceededException("Se alcanzo el limite de uso de IA para este mes."))
                .when(tenantAiSpendService).assertUnderLimit(1L);

        assertThrows(AiSpendLimitExceededException.class, () -> service.extract(file, null));

        verifyNoInteractions(aiService);
        verify(tenantAiSpendService, never()).recordSpend(any(), any());
    }

    @Test
    void propagatesAiServiceExceptionUnchangedAndDoesNotRecordSpend() {
        TenantContext.set(1L);
        MockMultipartFile file = new MockMultipartFile("file", "factura.jpg", "image/jpeg", new byte[]{1});
        AiServiceException error = new AiServiceException(AiServiceException.Reason.INVALID_JSON, "bad");
        when(aiService.parseLedgerMediaFromBytesWithUsage(any(), eq("image/jpeg"), eq(null))).thenThrow(error);

        AiServiceException thrown = assertThrows(AiServiceException.class, () -> service.extract(file, null));

        assertSame(error, thrown);
        verify(tenantAiSpendService, never()).recordSpend(any(), any());
    }

    private LedgerExtraction sampleExtraction() {
        return new LedgerExtraction("Factura", "ACME", null, null, null,
                new BigDecimal("14500"), LocalDate.of(2026, 7, 10), null, "desc", List.of(),
                null, null, null, null);
    }
}
