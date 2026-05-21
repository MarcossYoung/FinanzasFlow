package com.example.demo.service;

import com.example.demo.dto.ChatRequest;
import com.example.demo.dto.FinanceDashboardResponse;
import com.example.demo.dto.InvoiceResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
public class AiService {

    private final FinanceService financeService;
    private final InvoiceService invoiceService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${anthropic.api.key:}")
    private String apiKey;

    private String cachedDigestText = null;
    private String cachedDigestWeek = null;

    private static final String ANTHROPIC_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-haiku-4-5-20251001";

    public AiService(FinanceService financeService, InvoiceService invoiceService) {
        this.financeService = financeService;
        this.invoiceService = invoiceService;
    }

    private String callClaude(String systemPrompt, String userMessage, int maxTokens) {
        if (apiKey == null || apiKey.isBlank()) {
            return "IA no configurada: falta ANTHROPIC_API_KEY.";
        }

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", MODEL);
            body.put("max_tokens", maxTokens);
            body.put("system", systemPrompt);
            body.put("messages", List.of(Map.of("role", "user", "content", userMessage)));

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

            if (responseHolder[0] == null) return "";
            Map<String, Object> parsed = objectMapper.readValue(responseHolder[0], new TypeReference<>() {});
            List<?> content = (List<?>) parsed.get("content");
            if (content != null && !content.isEmpty()) {
                Map<?, ?> first = (Map<?, ?>) content.get(0);
                return (String) first.get("text");
            }
            return "";
        } catch (Exception e) {
            return "Error al contactar IA: " + e.getMessage();
        }
    }

    private Map<String, Object> callClaudeForJson(String systemPrompt, String userMessage, int maxTokens) {
        try {
            String raw = callClaude(systemPrompt, userMessage, maxTokens);
            if (raw == null || raw.isBlank() || raw.startsWith("IA no configurada") || raw.startsWith("Error al contactar IA")) {
                return Map.of();
            }
            String cleaned = raw.trim();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceAll("^```[a-zA-Z]*\\n?", "").replaceAll("```$", "").trim();
            }
            return objectMapper.readValue(cleaned, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
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
        String system = """
                Eres un asistente de FinanzasFlow para cargar facturas B2B y casos de cobranza.
                Extrae datos de emails, mensajes, PDFs transcritos o notas internas.
                Responde SOLO con JSON valido, sin markdown ni explicaciones, con estas claves exactas:
                titulo (string o null),
                customerName (string o null),
                cuitDni (string o null),
                customerEmail (string o null),
                clientPhone (string o null),
                precio (numero o null, importe total de la factura),
                amount (numero o null, pago inicial/parcial si se menciona),
                startDate (YYYY-MM-DD o null, fecha de emision),
                fechaEntrega (YYYY-MM-DD o null, fecha de vencimiento),
                fechaEstimada (YYYY-MM-DD o null, usar la misma fecha de vencimiento si aplica),
                notas (string o null),
                lineItems (array de objetos con description, quantity, unitPrice; [] si no hay detalle).
                Si no hay titulo explicito, crea uno breve como "Factura - {customerName}".
                No inventes importes, fechas, CUIT ni contacto.
                """;

        Map<String, Object> result = callClaudeForJson(
                system,
                "Descripcion de factura/cobranza: " + description,
                500
        );

        return result.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
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

    public void streamChat(ChatRequest req, String username, SseEmitter emitter) {
        String userRole = SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst().orElse("GESTOR");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try {
                String pageContext = buildPageContext(req.page());

                String system = String.format(
                        "Eres el asistente de FinanzasFlow, una plataforma B2B para facturas, cobranzas, pagos, clientes y finanzas. " +
                                "El usuario '%s' tiene rol '%s'. Responde siempre en espanol de forma concisa y operativa. " +
                                "No uses contexto de muebles, taller, produccion ni inventario. Contexto de la pagina actual:\n%s",
                        username, userRole, pageContext
                );

                List<Map<String, String>> messages = new ArrayList<>();
                List<Map<String, String>> history = req.history();
                if (history != null) {
                    int start = Math.max(0, history.size() - 4);
                    messages.addAll(history.subList(start, history.size()));
                }
                messages.add(Map.of("role", "user", "content", req.message()));

                Map<String, Object> body = new LinkedHashMap<>();
                body.put("model", MODEL);
                body.put("max_tokens", 500);
                body.put("stream", true);
                body.put("system", system);
                body.put("messages", messages);

                String bodyJson = objectMapper.writeValueAsString(body);

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
                            try (BufferedReader reader = new BufferedReader(
                                    new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    if (!line.startsWith("data: ")) continue;
                                    String data = line.substring(6).trim();
                                    if ("[DONE]".equals(data)) break;
                                    try {
                                        Map<String, Object> event = objectMapper.readValue(data, new TypeReference<>() {});
                                        String type = (String) event.get("type");
                                        if ("content_block_delta".equals(type)) {
                                            @SuppressWarnings("unchecked")
                                            Map<String, Object> delta = (Map<String, Object>) event.get("delta");
                                            if (delta != null && "text_delta".equals(delta.get("type"))) {
                                                String text = (String) delta.get("text");
                                                if (text != null && !text.isEmpty()) {
                                                    emitter.send(SseEmitter.event().data(text));
                                                }
                                            }
                                        } else if ("message_stop".equals(type)) {
                                            break;
                                        }
                                    } catch (Exception ignored) {
                                    }
                                }
                            }
                            return null;
                        }
                );
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        executor.shutdown();
    }

    private String buildPageContext(String page) {
        try {
            if ("finance".equals(page)) {
                LocalDate today = LocalDate.now();
                FinanceDashboardResponse data = financeService.dashboard(today.withDayOfMonth(1), today);
                return String.format("Pagina de finanzas. Importe facturado del mes: $%s, gastos: $%s, ganancia neta: $%s, efectivo cobrado: $%s",
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
