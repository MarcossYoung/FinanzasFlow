package com.example.demo.service;

import com.example.demo.dto.LedgerExtraction;
import com.example.demo.dto.LedgerIngestionResult;
import com.example.demo.exceptions.AiServiceException;
import com.example.demo.exceptions.TelegramApiException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

@Service
public class TelegramIngestionWorker {
    private static final Set<String> ALLOWED_MEDIA = Set.of(
            "application/pdf", "image/jpeg", "image/png", "image/webp"
    );
    private static final String READ_FAILURE_MESSAGE =
            "No pude leer datos suficientes. Envia una imagen mas clara, un PDF o texto completo.";

    private final AiService aiService;
    private final TelegramService telegramService;
    private final LedgerIngestionService ingestionService;

    public TelegramIngestionWorker(AiService aiService,
                                   TelegramService telegramService,
                                   LedgerIngestionService ingestionService) {
        this.aiService = aiService;
        this.telegramService = telegramService;
        this.ingestionService = ingestionService;
    }

    public void processText(Long ingestionId, String chatId, String text) {
        process(ingestionId, chatId, () -> aiService.parseLedgerText(text));
    }

    public void processMedia(Long ingestionId, String chatId, String fileId, String mediaType, String caption) {
        process(ingestionId, chatId, () -> {
            if (!ALLOWED_MEDIA.contains(mediaType)) {
                throw new IllegalArgumentException("Formato no soportado");
            }
            TelegramService.TelegramFile file = telegramService.getFile(fileId);
            byte[] bytes = telegramService.downloadFile(file.path());
            return aiService.parseLedgerMediaFromBytes(bytes, mediaType, caption);
        });
    }

    public void echoCompleted(Long ingestionId, String chatId, Long tenantId) {
        try {
            telegramService.sendMessage(chatId, completedText(
                    ingestionService.getCompletedResult(ingestionId, chatId, tenantId)));
        } catch (Exception ignored) {
            // A duplicate delivery must not mutate a completed ingestion.
        }
    }

    private void process(Long ingestionId, String chatId, ExtractionCall call) {
        try {
            LedgerExtraction extraction = call.extract();
            ingestionService.markPending(ingestionId, extraction);
            String callbackPrefix = "ledger:" + ingestionId + ":";
            long callbackMessageId = telegramService.sendMessageWithButtons(
                    chatId,
                    pendingText(ingestionService.normalizeExtraction(extraction)),
                    List.of(
                            new TelegramService.InlineButton("Cobro (factura)", callbackPrefix + "COBRO"),
                            new TelegramService.InlineButton("Gasto (proveedor)", callbackPrefix + "GASTO")
                    )
            );
            ingestionService.recordCallbackMessage(ingestionId, callbackMessageId);
        } catch (TelegramApiException e) {
            ingestionService.markFailed(ingestionId, "Telegram: " + e.getReason());
            if (e.getReason() == TelegramApiException.Reason.TOO_LARGE) {
                telegramService.sendMessage(chatId, "El archivo es demasiado grande. Envia uno de menor tamano.");
            } else if (e.getReason() == TelegramApiException.Reason.DOWNLOAD
                    || e.getReason() == TelegramApiException.Reason.METADATA) {
                telegramService.sendMessage(chatId, "No pude descargar el archivo. Reenvialo e intenta nuevamente.");
            }
        } catch (AiServiceException e) {
            ingestionService.markFailed(ingestionId, "Claude: " + e.getReason());
            String message = e.getReason() == AiServiceException.Reason.NOT_CONFIGURED
                    ? "La carga automatica no esta disponible temporalmente."
                    : READ_FAILURE_MESSAGE;
            telegramService.sendMessage(chatId, message);
        } catch (IllegalArgumentException e) {
            ingestionService.markFailed(ingestionId, e.getMessage());
            telegramService.sendMessage(chatId, READ_FAILURE_MESSAGE);
        } catch (Exception e) {
            ingestionService.markFailed(ingestionId, "Unexpected processing failure");
            telegramService.sendMessage(chatId, "La carga automatica no esta disponible temporalmente.");
        }
    }

    public String completedText(LedgerIngestionResult result) {
        if (result == null) {
            return "Guardado. Si algun dato esta mal, editalo desde el panel.";
        }
        String type = result.recordType() == null ? "registro" : result.recordType().name().toLowerCase();
        String recordId = result.recordId() == null ? "sin ID" : "#" + result.recordId();
        String date = result.date() == null ? "sin fecha" : result.date().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String counterparty = result.counterparty() == null || result.counterparty().isBlank()
                ? "Sin contraparte"
                : result.counterparty();
        return "Guardado como " + type + ": " + recordId
                + " - $" + money(result.amount())
                + " - " + counterparty + " - " + date
                + ". Si algun dato esta mal, editalo desde el panel.";
    }

    private String pendingText(LedgerExtraction extraction) {
        String counterparty = extraction == null ? null : extraction.counterpartyName();
        if (counterparty == null || counterparty.isBlank()) {
            counterparty = extraction == null ? null : extraction.cuitDni();
        }
        if (counterparty == null || counterparty.isBlank()) {
            counterparty = extraction == null ? null : extraction.email();
        }
        if (counterparty == null || counterparty.isBlank()) {
            counterparty = "Sin contraparte";
        }
        String date = extraction == null || extraction.issueDate() == null ? "sin fecha" : extraction.issueDate().toString();
        return "Detectado: $" + money(extraction == null ? null : extraction.amount())
                + " - " + counterparty + " - " + date + "\nComo queres guardarlo?";
    }

    private String money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    @FunctionalInterface
    private interface ExtractionCall {
        LedgerExtraction extract();
    }
}
