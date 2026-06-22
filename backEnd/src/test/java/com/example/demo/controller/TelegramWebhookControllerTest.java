package com.example.demo.controller;

import com.example.demo.model.TelegramIngestionStatus;
import com.example.demo.repository.*;
import com.example.demo.service.LedgerIngestionService;
import com.example.demo.service.TelegramIngestionWorker;
import com.example.demo.service.TelegramService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

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

    private TelegramWebhookController controller(String chats) {
        return new TelegramWebhookController(
                customerRepo, reminderRepo, invoiceRepo, workOrderRepo, telegramService,
                ingestionService, worker, directExecutor, chats, "secret", "1", "2");
    }
}
