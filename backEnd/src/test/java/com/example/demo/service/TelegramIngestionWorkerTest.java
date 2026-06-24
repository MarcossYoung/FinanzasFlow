package com.example.demo.service;

import com.example.demo.dto.LedgerExtraction;
import com.example.demo.dto.LedgerIngestionResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

class TelegramIngestionWorkerTest {
    private final AiService aiService = mock(AiService.class);
    private final TelegramService telegramService = mock(TelegramService.class);
    private final LedgerIngestionService ingestionService = mock(LedgerIngestionService.class);
    private final TelegramIngestionWorker worker = new TelegramIngestionWorker(aiService, telegramService, ingestionService);

    @Test
    void invalidExtractionMarksFailedAndDoesNotSendButtons() {
        LedgerExtraction invalid = new LedgerExtraction(null, "ACME", null, null, null,
                null, null, null, null, List.of());
        when(aiService.parseLedgerText("bad")).thenReturn(invalid);
        doThrow(new IllegalArgumentException("El total debe ser positivo"))
                .when(ingestionService).markPending(7L, invalid);

        worker.processText(7L, "42", "bad");

        verify(ingestionService).markFailed(7L, "El total debe ser positivo");
        verify(telegramService).sendMessage("42",
                "No pude leer datos suficientes. Envia una imagen mas clara, un PDF o texto completo.");
        verify(telegramService, never()).sendMessageWithButtons(anyString(), anyString(), anyList());
        verify(ingestionService, never()).recordCallbackMessage(anyLong(), anyLong());
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

        verify(ingestionService).markPending(7L, extraction);
        verify(telegramService).sendMessageWithButtons(eq("42"), contains("Detectado"), anyList());
        verify(ingestionService).recordCallbackMessage(7L, 99L);
    }
}
