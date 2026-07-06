package com.example.demo.service;

import com.example.demo.dto.LedgerExtraction;
import com.example.demo.dto.LedgerIngestionResult;
import com.example.demo.dto.LedgerLineItemExtraction;
import com.example.demo.exceptions.AiServiceException;
import com.example.demo.exceptions.TelegramApiException;
import com.example.demo.service.ingestion.PendingLedgerStore;
import org.springframework.beans.factory.annotation.Value;
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
    private final PendingLedgerStore store;
    private final boolean debugEcho;

    public TelegramIngestionWorker(AiService aiService,
                                   TelegramService telegramService,
                                   LedgerIngestionService ingestionService,
                                   PendingLedgerStore store,
                                   @Value("${telegram.ingestion.debug-echo:false}") boolean debugEcho) {
        this.aiService = aiService;
        this.telegramService = telegramService;
        this.ingestionService = ingestionService;
        this.store = store;
        this.debugEcho = debugEcho;
    }

    public void processText(long token, String chatId, String text) {
        if (debugEcho) {
            processDebug(token, chatId, () -> aiService.rawLedgerResponseFromText(text));
            return;
        }
        process(token, chatId, () -> aiService.parseLedgerText(text));
    }

    public void processMedia(long token, String chatId, String fileId, String mediaType, String caption) {
        if (debugEcho) {
            processMediaDebug(token, chatId, fileId, mediaType, caption);
            return;
        }
        process(token, chatId, () -> {
            if (!ALLOWED_MEDIA.contains(mediaType)) {
                throw new IllegalArgumentException("Formato no soportado");
            }
            TelegramService.TelegramFile file = telegramService.getFile(fileId);
            byte[] bytes = telegramService.downloadFile(file.path());
            return aiService.parseLedgerMediaFromBytes(bytes, mediaType, caption);
        });
    }

    private void process(long token, String chatId, ExtractionCall call) {
        try {
            LedgerExtraction extraction = ingestionService.normalizeExtraction(call.extract());
            store.attachExtraction(token, extraction);
            String callbackPrefix = "ledger:" + token + ":";
            telegramService.sendMessageWithButtons(
                    chatId,
                    pendingText(extraction),
                    List.of(
                            new TelegramService.InlineButton("Cobro (factura)", callbackPrefix + "COBRO"),
                            new TelegramService.InlineButton("Gasto (proveedor)", callbackPrefix + "GASTO"),
                            new TelegramService.InlineButton("Cancelar", callbackPrefix + "CANCELAR")
                    )
            );
        } catch (TelegramApiException e) {
            store.remove(token);
            if (e.getReason() == TelegramApiException.Reason.TOO_LARGE) {
                telegramService.sendMessage(chatId, "El archivo es demasiado grande. Envia uno de menor tamano.");
            } else if (e.getReason() == TelegramApiException.Reason.DOWNLOAD
                    || e.getReason() == TelegramApiException.Reason.METADATA) {
                telegramService.sendMessage(chatId, "No pude descargar el archivo. Reenvialo e intenta nuevamente.");
            }
        } catch (AiServiceException e) {
            store.remove(token);
            String message = e.getReason() == AiServiceException.Reason.NOT_CONFIGURED
                    ? "La carga automatica no esta disponible temporalmente."
                    : READ_FAILURE_MESSAGE;
            telegramService.sendMessage(chatId, message);
        } catch (IllegalArgumentException e) {
            store.remove(token);
            telegramService.sendMessage(chatId, READ_FAILURE_MESSAGE);
        } catch (Exception e) {
            store.remove(token);
            telegramService.sendMessage(chatId, "La carga automatica no esta disponible temporalmente.");
        }
    }

    private void processDebug(long token, String chatId, RawExtractionCall call) {
        try {
            String raw = call.extractRaw();
            telegramService.sendMessage(chatId, "[DEBUG] RAW IA:\n" + truncate(raw));
            try {
                LedgerExtraction extraction = aiService.parseLedgerExtraction(raw);
                LedgerExtraction normalized = ingestionService.normalizeExtraction(extraction);
                store.attachExtraction(token, normalized);
                String callbackPrefix = "ledger:" + token + ":";
                telegramService.sendMessage(chatId, debugText(normalized));
                telegramService.sendMessageWithButtons(
                        chatId,
                        pendingText(normalized),
                        List.of(
                                new TelegramService.InlineButton("Cobro (factura)", callbackPrefix + "COBRO"),
                                new TelegramService.InlineButton("Gasto (proveedor)", callbackPrefix + "GASTO"),
                                new TelegramService.InlineButton("Cancelar", callbackPrefix + "CANCELAR")
                        )
                );
            } catch (AiServiceException | IllegalArgumentException e) {
                store.remove(token);
                telegramService.sendMessage(chatId, "[DEBUG] RECHAZADO: " + safeMessage(e));
            }
        } catch (AiServiceException e) {
            store.remove(token);
            telegramService.sendMessage(chatId, "[DEBUG] ERROR IA: " + safeMessage(e));
        } catch (Exception e) {
            store.remove(token);
            telegramService.sendMessage(chatId, "La carga automatica no esta disponible temporalmente.");
        }
    }

    private void processMediaDebug(long token, String chatId, String fileId, String mediaType, String caption) {
        try {
            if (!ALLOWED_MEDIA.contains(mediaType)) {
                throw new IllegalArgumentException("Formato no soportado");
            }
            TelegramService.TelegramFile file = telegramService.getFile(fileId);
            byte[] bytes = telegramService.downloadFile(file.path());
            processDebug(token, chatId, () -> aiService.rawLedgerResponseFromMedia(bytes, mediaType, caption));
        } catch (TelegramApiException e) {
            store.remove(token);
            if (e.getReason() == TelegramApiException.Reason.TOO_LARGE) {
                telegramService.sendMessage(chatId, "El archivo es demasiado grande. Envia uno de menor tamano.");
            } else if (e.getReason() == TelegramApiException.Reason.DOWNLOAD
                    || e.getReason() == TelegramApiException.Reason.METADATA) {
                telegramService.sendMessage(chatId, "No pude descargar el archivo. Reenvialo e intenta nuevamente.");
            }
        } catch (IllegalArgumentException e) {
            store.remove(token);
            telegramService.sendMessage(chatId, READ_FAILURE_MESSAGE);
        } catch (Exception e) {
            store.remove(token);
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

        StringBuilder sb = new StringBuilder("Detectado: $")
                .append(money(extraction == null ? null : extraction.amount()))
                .append(" - ").append(counterparty)
                .append(" - ").append(date);

        String titulo = extraction == null ? null : extraction.titulo();
        if (titulo != null && !titulo.isBlank()) {
            sb.append("\nConcepto: ").append(titulo);
        }

        List<LedgerLineItemExtraction> rows = extraction == null || extraction.lineItems() == null
                ? List.of() : extraction.lineItems();
        if (!rows.isEmpty()) {
            sb.append("\nItems:");
            for (LedgerLineItemExtraction row : rows) {
                sb.append("\n- ").append(value(row.description()))
                        .append(" x").append(value(row.quantity()))
                        .append(" @ $").append(value(row.unitPrice()));
            }
        }

        sb.append("\nComo queres guardarlo? (Cancelar descarta esta carga)");
        return sb.toString();
    }

    private String money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String debugText(LedgerExtraction extraction) {
        StringBuilder sb = new StringBuilder("[DEBUG] PARSEADO:\n");
        sb.append("titulo: ").append(value(extraction.titulo())).append("\n");
        sb.append("counterpartyName: ").append(value(extraction.counterpartyName())).append("\n");
        sb.append("cuitDni: ").append(value(extraction.cuitDni())).append("\n");
        sb.append("email: ").append(value(extraction.email())).append("\n");
        sb.append("phone: ").append(value(extraction.phone())).append("\n");
        sb.append("amount: ").append(value(extraction.amount())).append("\n");
        sb.append("issueDate: ").append(value(extraction.issueDate())).append("\n");
        sb.append("dueDate: ").append(value(extraction.dueDate())).append("\n");
        sb.append("description: ").append(value(extraction.description())).append("\n");
        sb.append("lineItems:");
        List<LedgerLineItemExtraction> rows = extraction.lineItems() == null ? List.of() : extraction.lineItems();
        if (rows.isEmpty()) {
            sb.append(" []");
        } else {
            for (LedgerLineItemExtraction row : rows) {
                sb.append("\n- ")
                        .append(value(row.description()))
                        .append(" / qty=")
                        .append(value(row.quantity()))
                        .append(" / unitPrice=")
                        .append(value(row.unitPrice()));
            }
        }
        sb.append("\noriginName: ").append(value(extraction.originName()));
        sb.append("\noriginTaxId: ").append(value(extraction.originTaxId()));
        sb.append("\ndestinationName: ").append(value(extraction.destinationName()));
        sb.append("\ndestinationTaxId: ").append(value(extraction.destinationTaxId()));
        return truncate(sb.toString());
    }

    private String truncate(String value) {
        if (value == null) return "";
        int max = 3900;
        if (value.length() <= max) return value;
        return value.substring(0, max) + "\n...[truncado]";
    }

    private String value(Object value) {
        return value == null ? "null" : String.valueOf(value);
    }

    private String safeMessage(Exception e) {
        return e.getMessage() == null || e.getMessage().isBlank()
                ? e.getClass().getSimpleName()
                : e.getMessage();
    }

    @FunctionalInterface
    private interface ExtractionCall {
        LedgerExtraction extract();
    }

    @FunctionalInterface
    private interface RawExtractionCall {
        String extractRaw();
    }
}
