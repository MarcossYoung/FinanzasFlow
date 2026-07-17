package com.example.demo.service;

import com.example.demo.config.TenantContext;
import com.example.demo.dto.FinanceDashboardResponse;
import com.example.demo.dto.InvoiceResponse;
import com.example.demo.dto.AiUsage;
import com.example.demo.dto.LedgerLineItemExtraction;
import com.example.demo.dto.LedgerExtraction;
import com.example.demo.dto.LedgerExtractionResult;
import com.example.demo.exceptions.AiServiceException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AiService {

    private final FinanceService financeService;
    private final InvoiceService invoiceService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    @Value("${anthropic.api.key:}")
    private String apiKey;

    private final Map<Long, CachedDigest> weeklyDigestCache = new ConcurrentHashMap<>();
    private record CachedDigest(String week, String text) {}

    private static final String ANTHROPIC_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-haiku-4-5-20251001";
    private record AnthropicCallResult(String text, AiUsage usage) {}

    private static final String LEDGER_EXTRACTION_SYSTEM = """
            Eres un extractor de documentos contables para FinanzasFlow.
            Extrae datos sin decidir si el documento es un cobro o un gasto.
            Responde SOLO con JSON valido, sin markdown ni explicaciones, con estas claves exactas:
            titulo (string o null), counterpartyName (string o null), cuitDni (string o null),
            email (string o null), phone (string o null), amount (numero o null, total del documento),
            issueDate (YYYY-MM-DD o null), dueDate (YYYY-MM-DD o null), description (string o null),
            lineItems (array de objetos con description, quantity, unitPrice; [] si no hay detalle),
            originName (string o null), originTaxId (string o null),
            destinationName (string o null), destinationTaxId (string o null).
            Para comprobantes de transferencia bancaria: extrae el nombre y CUIT/CBU del emisor
            (Cuenta Origen / quien envia el dinero) en originName y originTaxId, y el nombre y
            CUIT/CBU del receptor (Cuenta Destino) en destinationName y destinationTaxId.
            En esos casos counterpartyName y cuitDni pueden quedar null.
            Para facturas o documentos con una sola contraparte: usa counterpartyName y cuitDni
            normalmente; deja originName, originTaxId, destinationName, destinationTaxId en null.
            No inventes importes, fechas, identidad ni contacto. Usa punto decimal y fechas ISO.
            """;

    public AiService(FinanceService financeService, InvoiceService invoiceService, RestTemplate restTemplate) {
        this.financeService = financeService;
        this.invoiceService = invoiceService;
        this.restTemplate = restTemplate;
    }

    private String postToAnthropic(Map<String, Object> body) {
        return postToAnthropicInternal(body).text();
    }

    private AnthropicCallResult postToAnthropicInternal(Map<String, Object> body) {
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
            AiUsage usage = usageFrom(parsed.get("usage"));
            List<?> content = (List<?>) parsed.get("content");
            if (content != null && !content.isEmpty()) {
                for (Object block : content) {
                    if (block instanceof Map<?, ?> item && "text".equals(item.get("type")) && item.get("text") instanceof String text) {
                        return new AnthropicCallResult(text, usage);
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

    private AiUsage usageFrom(Object rawUsage) {
        if (!(rawUsage instanceof Map<?, ?> usage)) {
            return new AiUsage(0, 0);
        }
        return new AiUsage(longValue(usage.get("input_tokens")), longValue(usage.get("output_tokens")));
    }

    private long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : 0;
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
        return postToAnthropic(buildVisionBody(systemPrompt, fileBytes, mediaType, userText, maxTokens));
    }

    private Map<String, Object> buildVisionBody(String systemPrompt, byte[] fileBytes, String mediaType,
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
        return textBody(systemPrompt, content, maxTokens);
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
            Object parsed = objectMapper.readValue(cleanJson(raw), Object.class);
            if (!(parsed instanceof Map<?, ?> map)) {
                throw new AiServiceException(AiServiceException.Reason.INVALID_JSON, "Claude extraction root must be an object");
            }
            LedgerExtraction extraction = new LedgerExtraction(
                    stringField(map, "titulo"),
                    stringField(map, "counterpartyName"),
                    stringField(map, "cuitDni"),
                    stringField(map, "email"),
                    stringField(map, "phone"),
                    decimalField(map, "amount"),
                    dateField(map, "issueDate"),
                    dateField(map, "dueDate"),
                    stringField(map, "description"),
                    lineItemsField(map),
                    stringField(map, "originName"),
                    stringField(map, "originTaxId"),
                    stringField(map, "destinationName"),
                    stringField(map, "destinationTaxId")
            );
            if (extraction.amount() == null || extraction.amount().signum() <= 0) {
                throw new AiServiceException(AiServiceException.Reason.INVALID_JSON, "Claude extraction omitted a positive amount");
            }
            if (isBlank(extraction.counterpartyName()) && isBlank(extraction.cuitDni())
                    && isBlank(extraction.email())
                    && isBlank(extraction.originName()) && isBlank(extraction.originTaxId())) {
                throw new AiServiceException(AiServiceException.Reason.INVALID_JSON, "Claude extraction omitted counterparty identity");
            }
            return extraction;
        } catch (AiServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new AiServiceException(AiServiceException.Reason.INVALID_JSON, "Claude returned invalid extraction JSON", e);
        }
    }

    private String stringField(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof String text) return text;
        throw new AiServiceException(AiServiceException.Reason.INVALID_JSON, "Claude returned invalid " + key);
    }

    private BigDecimal decimalField(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Number number) {
            try {
                return new BigDecimal(number.toString());
            } catch (NumberFormatException e) {
                throw new AiServiceException(AiServiceException.Reason.INVALID_JSON, "Claude returned invalid " + key, e);
            }
        }
        throw new AiServiceException(AiServiceException.Reason.INVALID_JSON, "Claude returned invalid " + key);
    }

    private LocalDate dateField(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof String text) {
            if (text.isBlank()) return null;
            try {
                return LocalDate.parse(text.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (Exception e) {
                throw new AiServiceException(AiServiceException.Reason.INVALID_JSON, "Claude returned invalid " + key, e);
            }
        }
        throw new AiServiceException(AiServiceException.Reason.INVALID_JSON, "Claude returned invalid " + key);
    }

    private List<LedgerLineItemExtraction> lineItemsField(Map<?, ?> map) {
        Object value = map.get("lineItems");
        if (value == null) return List.of();
        if (!(value instanceof List<?> rows)) {
            throw new AiServiceException(AiServiceException.Reason.INVALID_JSON, "Claude returned invalid lineItems");
        }
        return rows.stream()
                .map(row -> {
                    if (!(row instanceof Map<?, ?> item)) {
                        throw new AiServiceException(AiServiceException.Reason.INVALID_JSON, "Claude returned invalid line item");
                    }
                    return new LedgerLineItemExtraction(
                            stringField(item, "description"),
                            decimalField(item, "quantity"),
                            decimalField(item, "unitPrice")
                    );
                })
                .toList();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public LedgerExtraction parseLedgerText(String text) {
        return parseLedgerExtraction(rawLedgerResponseFromText(text));
    }

    public String rawLedgerResponseFromText(String text) {
        if (text == null || text.isBlank()) {
            throw new AiServiceException(AiServiceException.Reason.INVALID_JSON, "Ledger text is empty");
        }
        return postToAnthropic(textBody(
                LEDGER_EXTRACTION_SYSTEM,
                "Extrae los datos contables de este texto:\n" + text,
                500
        ));
    }

    public LedgerExtraction parseLedgerMediaFromBytes(byte[] bytes, String mediaType, String caption) {
        return parseLedgerExtraction(rawLedgerResponseFromMedia(bytes, mediaType, caption));
    }

    public String rawLedgerResponseFromMedia(byte[] bytes, String mediaType, String caption) {
        if (bytes == null || bytes.length == 0) {
            throw new AiServiceException(AiServiceException.Reason.INVALID_JSON, "Ledger media is empty");
        }
        return callClaudeVision(
                LEDGER_EXTRACTION_SYSTEM,
                bytes,
                mediaType,
                caption == null || caption.isBlank()
                        ? "Extrae los datos contables de este documento."
                        : "Contexto del usuario: " + caption,
                500
        );
    }

    public LedgerExtractionResult parseLedgerMediaFromBytesWithUsage(byte[] bytes, String mediaType, String caption) {
        if (bytes == null || bytes.length == 0) {
            throw new AiServiceException(AiServiceException.Reason.INVALID_JSON, "Ledger media is empty");
        }
        Map<String, Object> body = buildVisionBody(
                LEDGER_EXTRACTION_SYSTEM,
                bytes,
                mediaType,
                caption == null || caption.isBlank()
                        ? "Extrae los datos contables de este documento."
                        : "Contexto del usuario: " + caption,
                500
        );
        AnthropicCallResult result = postToAnthropicInternal(body);
        LedgerExtraction extraction = parseLedgerJson(result.text());
        return new LedgerExtractionResult(extraction, result.usage());
    }

    public LedgerExtraction parseLedgerExtraction(String raw) {
        return parseLedgerJson(raw);
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
        Long tenantId = TenantContext.get();
        LocalDate today = LocalDate.now();
        WeekFields wf = WeekFields.ISO;
        String currentWeek = today.getYear() + "-W" + String.format("%02d", today.get(wf.weekOfWeekBasedYear()));

        CachedDigest cached = tenantId != null ? weeklyDigestCache.get(tenantId) : null;
        if (cached != null && currentWeek.equals(cached.week())) {
            return Map.of("digest", cached.text(), "generatedAt", today.toString(), "week", currentWeek);
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
        if (tenantId != null) {
            weeklyDigestCache.put(tenantId, new CachedDigest(currentWeek, digest));
        }

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
