package com.example.demo.controller;

import com.example.demo.model.LedgerDirection;
import com.example.demo.model.LedgerRecordType;
import com.example.demo.model.AppUser;
import com.example.demo.dto.CreatePaymentRequest;
import com.example.demo.dto.LedgerExtraction;
import com.example.demo.dto.LedgerIngestionResult;
import com.example.demo.model.Invoice;
import com.example.demo.model.OrderPayments;
import com.example.demo.model.PaymentStatus;
import com.example.demo.model.TelegramConnection;
import com.example.demo.model.Tenant;
import com.example.demo.repository.*;
import com.example.demo.service.LedgerIngestionService;
import com.example.demo.service.PaymentService;
import com.example.demo.service.TelegramConnectionService;
import com.example.demo.service.TelegramIngestionWorker;
import com.example.demo.service.TelegramService;
import com.example.demo.service.ingestion.PendingLedger;
import com.example.demo.service.ingestion.PendingLedgerStore;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TelegramWebhookControllerTest {
    private final CustomerRepo customerRepo = mock(CustomerRepo.class);
    private final PaymentReminderRepo reminderRepo = mock(PaymentReminderRepo.class);
    private final InvoiceRepo invoiceRepo = mock(InvoiceRepo.class);
    private final PaymentRepo paymentRepo = mock(PaymentRepo.class);
    private final PaymentService paymentService = mock(PaymentService.class);
    private final WorkOrderRepo workOrderRepo = mock(WorkOrderRepo.class);
    private final TelegramService telegramService = mock(TelegramService.class);
    private final TelegramConnectionService connectionService = mock(TelegramConnectionService.class);
    private final LedgerIngestionService ingestionService = mock(LedgerIngestionService.class);
    private final TelegramIngestionWorker worker = mock(TelegramIngestionWorker.class);
    private final PendingLedgerStore pendingLedgerStore = mock(PendingLedgerStore.class);
    private final Executor directExecutor = Runnable::run;

    @Test
    void selectsLargestPhotoForConnectedChat() {
        when(connectionService.resolveConnection("42")).thenReturn(Optional.of(connection(1L, 2L)));
        when(pendingLedgerStore.claim("42", 9L, 1L)).thenReturn(Optional.of(7L));
        TelegramWebhookController controller = controller();
        Map<String, Object> update = Map.of("message", Map.of(
                "message_id", 9,
                "chat", Map.of("id", 42, "type", "private"),
                "caption", "factura",
                "photo", List.of(Map.of("file_id", "small"), Map.of("file_id", "large"))
        ));

        assertEquals(HttpStatus.OK, controller.webhook("secret", update).getStatusCode());
        verify(worker).processMedia(7L, "42", "large", "image/jpeg", "factura");
    }

    @Test
    void unknownPhotoGetsConnectInstructionsAndPerformsNoWork() {
        when(connectionService.resolveConnection("99")).thenReturn(Optional.empty());
        TelegramWebhookController controller = controller();
        Map<String, Object> update = Map.of("message", Map.of(
                "message_id", 9, "chat", Map.of("id", 99, "type", "private"),
                "photo", List.of(Map.of("file_id", "large"))
        ));

        assertEquals(HttpStatus.OK, controller.webhook("secret", update).getStatusCode());
        verify(telegramService).sendMessage(eq("99"), contains("/connect"));
        verifyNoInteractions(ingestionService, worker, pendingLedgerStore);
    }

    @Test
    void acceptedSecretStillReturnsOkWhenProcessingFails() {
        when(connectionService.resolveConnection("42")).thenReturn(Optional.of(connection(1L, 2L)));
        when(pendingLedgerStore.claim("42", 9L, 1L)).thenThrow(new RuntimeException("bad state"));
        TelegramWebhookController controller = controller();
        Map<String, Object> update = Map.of("message", Map.of(
                "message_id", 9,
                "chat", Map.of("id", 42, "type", "private"),
                "text", "Factura ACME 100"
        ));

        assertEquals(HttpStatus.OK, controller.webhook("secret", update).getStatusCode());
    }

    @Test
    void callbackFromConnectedChatUsesConnectionOwner() {
        LedgerIngestionResult result = new LedgerIngestionResult(
                7L, LedgerDirection.COBRO, LedgerRecordType.INVOICE, 11L,
                new BigDecimal("123.45"), "ACME", LocalDate.of(2026, 6, 24), false);
        PendingLedger pending = pending();
        when(connectionService.resolveConnection("42")).thenReturn(Optional.of(connection(1L, 2L)));
        when(pendingLedgerStore.get(7L)).thenReturn(Optional.of(pending));
        when(ingestionService.finalizeDirection(pending, 2L, LedgerDirection.COBRO)).thenReturn(result);
        when(worker.completedText(result)).thenReturn("Guardado como invoice: #11");
        TelegramWebhookController controller = controller();
        Map<String, Object> update = Map.of("callback_query", Map.of(
                "id", "callback-1",
                "from", Map.of("id", 99),
                "message", Map.of("message_id", 70, "chat", Map.of("id", 42)),
                "data", "ledger:7:COBRO"
        ));

        assertEquals(HttpStatus.OK, controller.webhook("secret", update).getStatusCode());
        verify(telegramService).answerCallbackQuery("callback-1");
        verify(ingestionService).finalizeDirection(pending, 2L, LedgerDirection.COBRO);
        verify(pendingLedgerStore).remove(7L);
        verify(telegramService).sendMessage("42", "Guardado como invoice: #11");
    }

    @Test
    void callbackWithoutConnectionStillAnswersAndDoesNotFinalize() {
        when(pendingLedgerStore.get(7L)).thenReturn(Optional.of(pending()));
        when(connectionService.resolveConnection("42")).thenReturn(Optional.empty());
        TelegramWebhookController controller = controller();
        Map<String, Object> update = Map.of("callback_query", Map.of(
                "id", "callback-1",
                "from", Map.of("id", 99),
                "message", Map.of("message_id", 70, "chat", Map.of("id", 42)),
                "data", "ledger:7:COBRO"
        ));

        assertEquals(HttpStatus.OK, controller.webhook("secret", update).getStatusCode());
        verify(telegramService).answerCallbackQuery("callback-1");
        verify(telegramService).sendMessage("42", "Este chat no esta conectado. Reconecta con /connect.");
        verify(ingestionService, never()).finalizeDirection(any(PendingLedger.class), anyLong(), any());
    }

    @Test
    void callbackFailureKeepsPendingTokenAndSendsSafeMessage() {
        PendingLedger pending = pending();
        when(pendingLedgerStore.get(7L)).thenReturn(Optional.of(pending));
        when(connectionService.resolveConnection("42")).thenReturn(Optional.of(connection(1L, 2L)));
        when(ingestionService.finalizeDirection(pending, 2L, LedgerDirection.COBRO))
                .thenThrow(new IllegalArgumentException("El total debe ser positivo"));
        TelegramWebhookController controller = controller();
        Map<String, Object> update = Map.of("callback_query", Map.of(
                "id", "callback-1",
                "from", Map.of("id", 42),
                "message", Map.of("message_id", 70, "chat", Map.of("id", 42)),
                "data", "ledger:7:COBRO"
        ));

        assertEquals(HttpStatus.OK, controller.webhook("secret", update).getStatusCode());
        verify(pendingLedgerStore, never()).remove(7L);
        verify(telegramService).sendMessage("42", "No pude guardar el registro. Revisa el documento o intenta nuevamente.");
    }

    @Test
    void callbackWithBrokenConnectionSendsErrorMessageAndDoesNotFinalize() {
        TelegramConnection brokenConn = mock(TelegramConnection.class);
        when(brokenConn.getTenant()).thenThrow(new RuntimeException("simulated lazy failure"));
        PendingLedger pending = pending();
        when(pendingLedgerStore.get(7L)).thenReturn(Optional.of(pending));
        when(connectionService.resolveConnection("42")).thenReturn(Optional.of(brokenConn));
        TelegramWebhookController controller = controller();
        Map<String, Object> update = Map.of("callback_query", Map.of(
                "id", "callback-1",
                "from", Map.of("id", 99),
                "message", Map.of("message_id", 70, "chat", Map.of("id", 42)),
                "data", "ledger:7:COBRO"
        ));

        assertEquals(HttpStatus.OK, controller.webhook("secret", update).getStatusCode());
        verify(telegramService).sendMessage("42",
                "No pude guardar el registro. Revisa el documento o intenta nuevamente.");
        verify(pendingLedgerStore, never()).remove(anyLong());
        verify(ingestionService, never()).finalizeDirection(any(PendingLedger.class), anyLong(), any());
    }

    @Test
    void callbackWithoutMessageIdStillFinalizesPendingIngestion() {
        PendingLedger pending = pending();
        LedgerIngestionResult result = new LedgerIngestionResult(
                7L, LedgerDirection.COBRO, LedgerRecordType.INVOICE, 11L,
                new BigDecimal("123.45"), "ACME", LocalDate.of(2026, 6, 24), false);
        when(pendingLedgerStore.get(7L)).thenReturn(Optional.of(pending));
        when(connectionService.resolveConnection("42")).thenReturn(Optional.of(connection(1L, 2L)));
        when(ingestionService.finalizeDirection(pending, 2L, LedgerDirection.COBRO)).thenReturn(result);
        when(worker.completedText(result)).thenReturn("Guardado como invoice: #11");
        TelegramWebhookController controller = controller();
        Map<String, Object> update = Map.of("callback_query", Map.of(
                "id", "callback-1",
                "from", Map.of("id", 42),
                "message", Map.of("chat", Map.of("id", 42)),
                "data", "ledger:7:COBRO"
        ));

        assertEquals(HttpStatus.OK, controller.webhook("secret", update).getStatusCode());
        verify(ingestionService).finalizeDirection(pending, 2L, LedgerDirection.COBRO);
        verify(pendingLedgerStore).remove(7L);
        verify(telegramService).sendMessage("42", "Guardado como invoice: #11");
    }

    @Test
    void debugModeEchoesPayloadAndDoesNotPersist() {
        PendingLedger pending = pendingWithExtraction();
        when(pendingLedgerStore.get(7L)).thenReturn(Optional.of(pending));
        when(connectionService.resolveConnection("42")).thenReturn(Optional.of(connection(1L, 2L)));
        when(ingestionService.previewDirection(pending, LedgerDirection.COBRO))
                .thenReturn("InvoiceCreateRequest[titulo=Factura]");
        TelegramWebhookController controller = controller(true);
        Map<String, Object> update = Map.of("callback_query", Map.of(
                "id", "callback-1",
                "from", Map.of("id", 42),
                "message", Map.of("chat", Map.of("id", 42)),
                "data", "ledger:7:COBRO"
        ));

        assertEquals(HttpStatus.OK, controller.webhook("secret", update).getStatusCode());
        verify(ingestionService).previewDirection(pending, LedgerDirection.COBRO);
        verify(ingestionService, never()).finalizeDirection(any(PendingLedger.class), anyLong(), any());
        verify(pendingLedgerStore).remove(7L);
        verify(telegramService).sendMessage(eq("42"), contains("[DEBUG] NO GUARDADO"));
    }

    @Test
    void connectCommandConsumesCodeBeforeConnectionGate() {
        TelegramWebhookController controller = controller();
        Map<String, Object> update = Map.of("message", Map.of(
                "message_id", 9,
                "chat", Map.of("id", 42, "type", "private", "first_name", "Marco"),
                "text", "/connect ABCD-2345"
        ));

        assertEquals(HttpStatus.OK, controller.webhook("secret", update).getStatusCode());
        verify(connectionService).consumeConnectCode("ABCD-2345", "42", "private", "Marco");
        verify(connectionService, never()).resolveConnection(anyString());
    }

    @Test
    void pagoCommandRegistersPaymentAndMarksInvoicePaidWhenFullyCovered() {
        Tenant tenant = new Tenant();
        tenant.setId(1L);
        Invoice invoice = new Invoice();
        invoice.setId(1L);
        invoice.setTenant(tenant);
        invoice.setPrecio(new BigDecimal("100.00"));
        invoice.setPagoStatus(PaymentStatus.PENDIENTE);
        OrderPayments existingPayment = new OrderPayments();
        existingPayment.setAmount(new BigDecimal("60.00"));
        OrderPayments newPayment = new OrderPayments();
        newPayment.setAmount(new BigDecimal("40.00"));

        when(connectionService.resolveConnection("42")).thenReturn(Optional.of(connection(1L, 2L)));
        when(invoiceRepo.findByIdAndTenant_Id(1L, 1L)).thenReturn(Optional.of(invoice));
        when(paymentService.createPayment(any(CreatePaymentRequest.class))).thenReturn(newPayment);
        when(paymentRepo.findByInvoice_IdAndTenant_Id(1L, 1L))
                .thenReturn(List.of(existingPayment, newPayment));

        TelegramWebhookController controller = controller();
        Map<String, Object> update = Map.of("message", Map.of(
                "message_id", 9,
                "chat", Map.of("id", 42, "type", "private"),
                "text", "/pago #1 $40,00"
        ));

        assertEquals(HttpStatus.OK, controller.webhook("secret", update).getStatusCode());
        org.mockito.ArgumentCaptor<CreatePaymentRequest> requestCaptor =
                org.mockito.ArgumentCaptor.forClass(CreatePaymentRequest.class);
        verify(paymentService).createPayment(requestCaptor.capture());
        assertEquals(new BigDecimal("40.00"), requestCaptor.getValue().valor());
        assertEquals("RESTO", requestCaptor.getValue().type());
        assertEquals(1L, requestCaptor.getValue().product_id());
        assertEquals(LocalDate.now().toString(), requestCaptor.getValue().fecha());
        assertEquals("CASH", requestCaptor.getValue().paymentMethod());
        assertEquals(PaymentStatus.PAGADO, invoice.getPagoStatus());
        verify(invoiceRepo).save(invoice);
        verify(telegramService).sendMessage(eq("42"), contains("PAGADO COMPLETO"));
    }

    @Test
    void pagoCommandRejectsMalformedAmountWithoutCallingPaymentService() {
        when(connectionService.resolveConnection("42")).thenReturn(Optional.of(connection(1L, 2L)));
        TelegramWebhookController controller = controller();
        Map<String, Object> update = Map.of("message", Map.of(
                "message_id", 9,
                "chat", Map.of("id", 42, "type", "private"),
                "text", "/pago 1 abc"
        ));

        assertEquals(HttpStatus.OK, controller.webhook("secret", update).getStatusCode());
        verify(telegramService).sendMessage("42", "Monto invalido: abc");
        verifyNoInteractions(paymentService);
    }

    @Test
    void pagoCommandReportsMissingTenantInvoice() {
        when(connectionService.resolveConnection("42")).thenReturn(Optional.of(connection(1L, 2L)));
        when(invoiceRepo.findByIdAndTenant_Id(123L, 1L)).thenReturn(Optional.empty());
        TelegramWebhookController controller = controller();
        Map<String, Object> update = Map.of("message", Map.of(
                "message_id", 9,
                "chat", Map.of("id", 42, "type", "private"),
                "text", "/pago 123 5000"
        ));

        assertEquals(HttpStatus.OK, controller.webhook("secret", update).getStatusCode());
        verify(telegramService).sendMessage("42", "No encontre la factura #123.");
        verifyNoInteractions(paymentService);
    }

    @Test
    void helpIncludesPagoCommand() {
        when(connectionService.resolveConnection("42")).thenReturn(Optional.of(connection(1L, 2L)));
        TelegramWebhookController controller = controller();
        Map<String, Object> update = Map.of("message", Map.of(
                "message_id", 9,
                "chat", Map.of("id", 42, "type", "private"),
                "text", "/help"
        ));

        assertEquals(HttpStatus.OK, controller.webhook("secret", update).getStatusCode());
        verify(telegramService).sendMessage(eq("42"), argThat(message ->
                message != null && message.contains("/pago <facturaId> <monto>")));
    }

    private TelegramWebhookController controller() {
        return controller(false);
    }

    private TelegramWebhookController controller(boolean debugEcho) {
        return new TelegramWebhookController(
                customerRepo, reminderRepo, invoiceRepo, paymentRepo, paymentService, workOrderRepo, telegramService,
                connectionService, ingestionService, worker, pendingLedgerStore, directExecutor, "secret", debugEcho);
    }

    private PendingLedger pending() {
        return new PendingLedger(7L, "42", 9L, 1L, null, Instant.now());
    }

    private PendingLedger pendingWithExtraction() {
        LedgerExtraction extraction = new LedgerExtraction("Factura", "ACME", null, null, null,
                new BigDecimal("123.45"), LocalDate.of(2026, 6, 24), null, null, List.of(),
                null, null, null, null);
        return new PendingLedger(7L, "42", 9L, 1L, extraction, Instant.now());
    }

    private TelegramConnection connection(Long tenantId, Long ownerId) {
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        AppUser owner = new AppUser();
        owner.setId(ownerId);
        owner.setTenant(tenant);
        TelegramConnection connection = new TelegramConnection();
        connection.setChatId("42");
        connection.setTenant(tenant);
        connection.setDefaultOwner(owner);
        connection.setChatType("private");
        connection.setEnabled(true);
        return connection;
    }
}
