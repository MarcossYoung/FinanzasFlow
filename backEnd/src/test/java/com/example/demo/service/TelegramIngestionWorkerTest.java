package com.example.demo.service;

import com.example.demo.dto.LedgerExtraction;
import com.example.demo.dto.LedgerIngestionResult;
import com.example.demo.service.ingestion.PendingLedgerStore;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

class TelegramIngestionWorkerTest {
    private final AiService aiService = mock(AiService.class);
    private final TelegramService telegramService = mock(TelegramService.class);
    private final LedgerIngestionService ingestionService = mock(LedgerIngestionService.class);
    private final PendingLedgerStore store = mock(PendingLedgerStore.class);
    private final TelegramIngestionWorker worker = new TelegramIngestionWorker(aiService, telegramService, ingestionService, store);

    @Test
    void invalidExtractionMarksFailedAndDoesNotSendButtons() {
        LedgerExtraction invalid = new LedgerExtraction(null, "ACME", null, null, null,
                null, null, null, null, List.of());
        when(aiService.parseLedgerText("bad")).thenReturn(invalid);
        doThrow(new IllegalArgumentException("El total debe ser positivo"))
                .when(ingestionService).normalizeExtraction(invalid);

        worker.processText(7L, "42", "bad");

        verify(store).remove(7L);
        verify(telegramService).sendMessage("42",
                "No pude leer datos suficientes. Envia una imagen mas clara, un PDF o texto completo.");
        verify(telegramService, never()).sendMessageWithButtons(anyString(), anyString(), anyList());
        verify(store, never()).attachExtraction(anyLong(), any());
    }

    @Test
    void completedTextIsNullSafe() {
        assertDoesNotThrow(() -> worker.completedText(null));
        assertDoesNotThrow(() -> worker.completedText(new LedgerIngestionResult(
                7L, null, null, null, null, null, null, false)));
    }

    @Test
    void validExtractionSendsButtonsOnlyAfterPendingStateIsAccepted() {
        LedgerExtraction extraction = new LedgerExtraction(null, "ACME", null, null, null,
                new BigDecimal("20.00"), null, null, null, List.of());
        when(aiService.parseLedgerText("factura")).thenReturn(extraction);
        when(ingestionService.normalizeExtraction(extraction)).thenReturn(extraction);
        when(telegramService.sendMessageWithButtons(eq("42"), anyString(), anyList())).thenReturn(99L);

        worker.processText(7L, "42", "factura");

        verify(store).attachExtraction(7L, extraction);
        verify(telegramService).sendMessageWithButtons(eq("42"), contains("Detectado"), anyList());
    }
}
