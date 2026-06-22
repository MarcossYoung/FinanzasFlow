package com.example.demo.service;

import com.example.demo.dto.FinanceDashboardResponse;
import com.example.demo.dto.InvoiceResponse;
import com.example.demo.dto.LedgerExtraction;
import com.example.demo.exceptions.AiServiceException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Base64;

@Service
public class AiService {

    private final FinanceService financeService;
    private final InvoiceService invoiceService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Value("${anthropic.api.key:}")
    private String apiKey;

    private String cachedDigestText = null;
    private String cachedDigestWeek = null;

    private static final String ANTHROPIC_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-haiku-4-5-20251001";
    private static final String LEDGER_EXTRACTION_SYSTEM = """
            Eres un extractor de documentos contables para FinanzasFlow.
            Extrae datos sin decidir si el documento es un cobro o un gasto.
            Responde SOLO con JSON valido, sin markdown ni explicaciones, con estas claves exactas:
            titulo (string o null), counterpartyName (string o null), cuitDni (string o null),
            email (string o null), phone (string o null), amount (numero o null, total del documento),
            issueDate (YYYY-MM-DD o null), dueDate (YYYY-MM-DD o null), description (string o null),
            lineItems (array de objetos con description, quantity, unitPrice; [] si no hay detalle).
            No inventes importes, fechas, identidad ni contacto. Usa punto decimal y fechas ISO.
            """;

    public AiService(FinanceService financeService, InvoiceService invoiceService, RestTemplate restTemplate) {
        this.financeService = financeService;
        this.invoiceService = invoiceService;
        this.restTemplate = restTemplate;
    }

    private String postToAnthropic(Map<String, Object> body) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiServiceException(AiServiceException.Reason.NOT_CONFIGURED, "Anthropic is not configured");
        }
        try {
            String bodyJson = objectMapper.writeValueAsString(body);
            String[] responseHolder = {null};

            restTemplate.execute(
                    ANTHROPIC_URL,
                    HttpMethod.POST,
                    request -> {
                        request.getHeaders().set("x-api-key", apiKey);
                        request.getHeaders().set("anthropic-version", "2023-06-01");
                        request.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                        request.getBody().write(bodyJson.getBytes(StandardCharsets.UTF_8));
                    },
                    response -> {
                        responseHolder[0] = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        return null;
                    }
            );

            if (responseHolder[0] == null || responseHolder[0].isBlank()) {
                throw new AiServiceException(AiServiceException.Reason.EMPTY_RESPONSE, "Anthropic returned an empty response");
            }
            Map<String, Object> parsed = objectMapper.readValue(responseHolder[0], new TypeReference<>() {});
            List<?> content = (List<?>) parsed.get("content");
            if (content != null && !content.isEmpty()) {
                for (Object block : content) {
                    if (block instanceof Map<?, ?> item && "text".equals(item.get("type")) && item.get("text") instanceof String text) {
                        return text;
                    }
                }
            }
            throw new AiServiceException(AiServiceException.Reason.EMPTY_RESPONSE, "Anthropic returned no text block");
        } catch (AiServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new AiServiceException(AiServiceException.Reason.HTTP_ERROR, "Anthropic request failed", e);
        }
    }

    private Map<String, Object> textBody(String systemPrompt, Object content, int maxTokens) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", MODEL);
        body.put("max_tokens", maxTokens);
        body.put("system", systemPrompt);
        body.put("messages", List.of(Map.of("role", "user", "content", content)));
        return body;
    }

    private String callClaude(String systemPrompt, String userMessage, int maxTokens) {
        try {
            return postToAnthropic(textBody(systemPrompt, userMessage, maxTokens));
        } catch (AiServiceException e) {
            return e.getReason() == AiServiceException.Reason.NOT_CONFIGURED
                    ? "IA no configurada: falta ANTHROPIC_API_KEY."
                    : "Error al contactar IA.";
        }
    }

    public String callClaudeVision(String systemPrompt, byte[] fileBytes, String mediaType,
                                   String userText, int maxTokens) {
        String blockType = "application/pdf".equals(mediaType) ? "document" : "image";
        Map<String, Object> source = Map.of(
                "type", "base64",
                "media_type", mediaType,
                "data", Base64.getEncoder().encodeToString(fileBytes)
        );
        List<Map<String, Object>> content = List.of(
                Map.of("type", blockType, "source", source),
                Map.of("type", "text", "text", userText == null || userText.isBlank()
                        ? "Extrae los datos contables de este documento." : userText)
        );
        return postToAnthropic(textBody(systemPrompt, content, maxTokens));
    }

    private Map<String, Object> callClaudeForJson(String systemPrompt, String userMessage, int maxTokens) {
        try {
            String raw = postToAnthropic(textBody(systemPrompt, userMessage, maxTokens));
            String cleaned = cleanJson(raw);
            return objectMapper.readValue(cleaned, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String cleanJson(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new AiServiceException(AiServiceException.Reason.EMPTY_RESPONSE, "Claude returned empty extraction");
        }
        String cleaned = raw.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("^```[a-zA-Z]*\\n?", "").replaceAll("```$", "").trim();
        }
        return cleaned;
    }

    private LedgerExtraction parseLedgerJson(String raw) {
        try {
            return objectMapper.readValue(cleanJson(raw), LedgerExtraction.class);
        } catch (AiServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new AiServiceException(AiServiceException.Reason.INVALID_JSON, "Claude returned invalid extraction JSON", e);
        }
    }

    public LedgerExtraction parseLedgerText(String text) {
        if (text == null || text.isBlank()) {
            throw new AiServiceException(AiServiceException.Reason.INVALID_JSON, "Ledger text is empty");
        }
        return parseLedgerJson(postToAnthropic(textBody(
                LEDGER_EXTRACTION_SYSTEM,
                "Extrae los datos contables de este texto:\n" + text,
                500
        )));
    }

    public LedgerExtraction parseLedgerMediaFromBytes(byte[] bytes, String mediaType, String caption) {
        if (bytes == null || bytes.length == 0) {
            throw new AiServiceException(AiServiceException.Reason.INVALID_JSON, "Ledger media is empty");
        }
        return parseLedgerJson(callClaudeVision(
                LEDGER_EXTRACTION_SYSTEM,
                bytes,
                mediaType,
                caption == null || caption.isBlank()
                        ? "Extrae los datos contables de este documento."
                        : "Contexto del usuario: " + caption,
                500
        ));
    }

    public String generateFinanceInsight(String from, String to) {
        FinanceDashboardResponse data = financeService.dashboard(LocalDate.parse(from), LocalDate.parse(to));

        String context = String.format(
                "Periodo: %s a %s\nImporte facturado: $%s\nEfectivo cobrado: $%s\nGastos: $%s\nGanancia neta: $%s\n" +
                        "Desglose de gastos: %s\nRendimiento por gestor: %s",
                from, to,
                data.tInc(), data.tDep(), data.tExp(), data.netProfit(),
                data.expenseBreakdown(), data.userStats()
        );

        String system = "Eres un analista financiero de FinanzasFlow, una plataforma B2B de facturas, cobranzas y flujo de caja. " +
                "Responde siempre en espanol. Se conciso, en 3 o 4 oraciones. " +
                "Analiza facturacion, efectivo cobrado, gastos, rentabilidad y riesgos de cobranza con acciones concretas.";

        return callClaude(system, "Analiza estos datos financieros:\n" + context, 400);
    }

    // Kept method name for existing /api/ai/parse-order endpoint compatibility.
    public Map<String, Object> parseOrderDescription(String description) {
        try {
            LedgerExtraction extraction = parseLedgerText(description);
            Map<String, Object> result = new LinkedHashMap<>();
            putIfNotNull(result, "titulo", extraction.titulo());
            putIfNotNull(result, "customerName", extraction.counterpartyName());
            putIfNotNull(result, "cuitDni", extraction.cuitDni());
            putIfNotNull(result, "customerEmail", extraction.email());
            putIfNotNull(result, "clientPhone", extraction.phone());
            putIfNotNull(result, "precio", extraction.amount());
            putIfNotNull(result, "startDate", extraction.issueDate());
            putIfNotNull(result, "fechaEntrega", extraction.dueDate());
            putIfNotNull(result, "fechaEstimada", extraction.dueDate());
            putIfNotNull(result, "notas", extraction.description());
            result.put("lineItems", extraction.lineItems());
            return result;
        } catch (AiServiceException e) {
            return Map.of();
        }
    }

    private void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) target.put(key, value);
    }

    public Map<String, Object> generateWeeklyDigest() {
        LocalDate today = LocalDate.now();
        WeekFields wf = WeekFields.ISO;
        String currentWeek = today.getYear() + "-W" + String.format("%02d", today.get(wf.weekOfWeekBasedYear()));

        if (currentWeek.equals(cachedDigestWeek) && cachedDigestText != null) {
            return Map.of("digest", cachedDigestText, "generatedAt", today.toString(), "week", currentWeek);
        }

        List<InvoiceResponse> disputed = invoiceService.getProductsPastDue();
        List<InvoiceResponse> promisedPayments = invoiceService.getProductsNotPickedUp();
        List<InvoiceResponse> dueThisWeek = invoiceService.getProductsDueThisWeek();

        LocalDate firstOfMonth = today.withDayOfMonth(1);
        FinanceDashboardResponse finance = financeService.dashboard(firstOfMonth, today);

        String oldestPromisedPayment = promisedPayments.isEmpty() ? "ninguno" :
                promisedPayments.get(0).titulo() != null ? promisedPayments.get(0).titulo() : "desconocido";

        String context = String.format(
                "Facturas en disputa o vencidas: %d\nPromesas de pago activas: %d (mas antigua: %s)\n" +
                        "Facturas por vencer esta semana: %d\nGanancia neta del mes: $%s\nImporte facturado del mes: $%s\nEfectivo cobrado del mes: $%s",
                disputed.size(), promisedPayments.size(), oldestPromisedPayment,
                dueThisWeek.size(), finance.netProfit(), finance.tInc(), finance.tDep()
        );

        String system = "Eres un asistente ejecutivo de FinanzasFlow para una operacion B2B de facturas y cobranzas. " +
                "Responde en espanol con 2 o 3 oraciones cortas y directas. " +
                "Prioriza riesgo de cobranza, efectivo esperado, facturas por vencer y acciones de seguimiento.";

        String digest = callClaude(system, "Resumen semanal del negocio:\n" + context, 150);
        cachedDigestText = digest;
        cachedDigestWeek = currentWeek;

        return Map.of("digest", digest, "generatedAt", today.toString(), "week", currentWeek);
    }

    private String buildPageContext(String page) {
        try {
            if ("finance".equals(page)) {
                LocalDate today = LocalDate.now();
                FinanceDashboardResponse data = financeService.dashboard(today.withDayOfMonth(1), today);
                return String.format("Página de finanzas. Importe facturado del mes: $%s, gastos: $%s, ganancia neta: $%s, efectivo cobrado: $%s",
                        data.tInc(), data.tExp(), data.netProfit(), data.tDep());
            } else if ("dashboard".equals(page)) {
                int disputed = invoiceService.getProductsPastDue().size();
                int dueThisWeek = invoiceService.getProductsDueThisWeek().size();
                int promisedPayments = invoiceService.getProductsNotPickedUp().size();
                return String.format("Dashboard de facturas. Facturas en disputa o vencidas: %d, facturas por vencer esta semana: %d, promesas de pago activas: %d",
                        disputed, dueThisWeek, promisedPayments);
            }
        } catch (Exception ignored) {
        }
        return "Sin datos especificos de pagina disponibles.";
    }

    public Map<String, Object> parseSearchQuery(String query) {
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String system = String.format("""
                Hoy es %s. Eres un asistente que extrae filtros de busqueda para facturas, clientes y cobranzas B2B.
                Responde SOLO con JSON valido, sin markdown, con estas claves exactas:
                titulo (texto o null),
                customerName (texto o null),
                cuitDni (texto o null),
                paymentStatus (texto o null),
                workOrderStatus (uno de: EN_GESTION|CONTACTADO|PROMETIO_PAGO|EN_DISPUTA|INCOBRABLE|CERRADO, o null),
                amountMin (numero o null),
                amountMax (numero o null),
                from (YYYY-MM-DD o null),
                to (YYYY-MM-DD o null).
                Interpreta "vencidas", "atrasadas" o "en problema" como EN_DISPUTA salvo que el usuario pida INCOBRABLE.
                Interpreta "prometio pago" o "promesas" como PROMETIO_PAGO.
                Interpreta "cerradas", "cobradas" o "finalizadas" como CERRADO.
                Los campos no mencionados deben ser null.
                """, today);

        return callClaudeForJson(system, "Busqueda: " + query, 250);
    }
}
