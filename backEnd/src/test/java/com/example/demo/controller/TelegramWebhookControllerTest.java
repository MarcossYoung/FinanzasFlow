package com.example.demo.controller;

import com.example.demo.model.TelegramIngestionStatus;
import com.example.demo.model.LedgerDirection;
import com.example.demo.model.LedgerRecordType;
import com.example.demo.dto.LedgerIngestionResult;
import com.example.demo.repository.*;
import com.example.demo.service.LedgerIngestionService;
import com.example.demo.service.TelegramIngestionWorker;
import com.example.demo.service.TelegramService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TelegramWebhookControllerTest {
    private final CustomerRepo customerRepo = mock(CustomerRepo.class);
    private final PaymentReminderRepo reminderRepo = mock(PaymentReminderRepo.class);
    private final InvoiceRepo invoiceRepo = mock(InvoiceRepo.class);
    private final WorkOrderRepo workOrderRepo = mock(WorkOrderRepo.class);
    private final TelegramService telegramService = mock(TelegramService.class);
    private final LedgerIngestionService ingestionService = mock(LedgerIngestionService.class);
    private final TelegramIngestionWorker worker = mock(TelegramIngestionWorker.class);
    private final Executor directExecutor = Runnable::run;

    @Test
    void selectsLargestPhotoForAuthorizedChat() {
        when(ingestionService.claim("42", 9L, 1L))
                .thenReturn(new LedgerIngestionService.ClaimResult(7L, true, TelegramIngestionStatus.PROCESSING));
        TelegramWebhookController controller = controller("42");
        Map<String, Object> update = Map.of("message", Map.of(
                "message_id", 9,
                "chat", Map.of("id", 42),
                "caption", "factura",
                "photo", List.of(Map.of("file_id", "small"), Map.of("file_id", "large"))
        ));

        assertEquals(HttpStatus.OK, controller.webhook("secret", update).getStatusCode());
        verify(worker).processMedia(7L, "42", "large", "image/jpeg", "factura");
    }

    @Test
    void unauthorizedPhotoPerformsNoWork() {
        TelegramWebhookController controller = controller("42");
        Map<String, Object> update = Map.of("message", Map.of(
                "message_id", 9, "chat", Map.of("id", 99),
                "photo", List.of(Map.of("file_id", "large"))
        ));

        assertEquals(HttpStatus.OK, controller.webhook("secret", update).getStatusCode());
        verifyNoInteractions(ingestionService, worker, telegramService);
    }

    @Test
    void callbackFromAuthorizedChatAllowsDifferentSenderId() {
        LedgerIngestionResult result = new LedgerIngestionResult(
                7L, LedgerDirection.COBRO, LedgerRecordType.INVOICE, 11L,
                new BigDecimal("123.45"), "ACME", LocalDate.of(2026, 6, 24), false);
        when(ingestionService.finalizeDirection(7L, "42", 1L, 2L, LedgerDirection.COBRO))
                .thenReturn(result);
        when(worker.completedText(result)).thenReturn("Guardado como invoice: #11");
        TelegramWebhookController controller = controller("42");
        Map<String, Object> update = Map.of("callback_query", Map.of(
                "id", "callback-1",
                "from", Map.of("id", 99),
                "message", Map.of("chat", Map.of("id", 42)),
                "data", "ledger:7:COBRO"
        ));

        assertEquals(HttpStatus.OK, controller.webhook("secret", update).getStatusCode());
        verify(telegramService).answerCallbackQuery("callback-1");
        verify(ingestionService).finalizeDirection(7L, "42", 1L, 2L, LedgerDirection.COBRO);
        verify(telegramService).sendMessage("42", "Guardado como invoice: #11");
    }

    private TelegramWebhookController controller(String chats) {
        return new TelegramWebhookController(
                customerRepo, reminderRepo, invoiceRepo, workOrderRepo, telegramService,
                ingestionService, worker, directExecutor, chats, "secret", "1", "2");
    }
}
