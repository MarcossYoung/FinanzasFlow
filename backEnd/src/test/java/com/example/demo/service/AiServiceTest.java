package com.example.demo.service;

import com.example.demo.dto.LedgerExtraction;
import com.example.demo.exceptions.AiServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AiServiceTest {
    @Test
    void buildsImageAndPdfContentBlocks() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        AiService service = service(restTemplate);
        String response = "{\"content\":[{\"type\":\"text\",\"text\":\"ok\"}]}";

        server.expect(requestTo(containsString("/v1/messages")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"type\":\"image\"")))
                .andExpect(content().string(containsString("\"media_type\":\"image/jpeg\"")))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/v1/messages")))
                .andExpect(content().string(containsString("\"type\":\"document\"")))
                .andExpect(content().string(containsString("\"media_type\":\"application/pdf\"")))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        assertEquals("ok", service.callClaudeVision("system", new byte[]{1}, "image/jpeg", "caption", 50));
        assertEquals("ok", service.callClaudeVision("system", new byte[]{2}, "application/pdf", "", 50));
        server.verify();
    }

    @Test
    void parsesFencedTypedExtractionAndRejectsMalformedJson() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        AiService service = service(restTemplate);
        String extraction = "```json\n{\"titulo\":\"Factura\",\"counterpartyName\":\"ACME\",\"amount\":14500,\"issueDate\":\"2026-06-22\",\"lineItems\":[]}\n```";
        String validResponse = "{\"content\":[{\"type\":\"text\",\"text\":" + jsonString(extraction) + "}]}";
        server.expect(requestTo(containsString("/v1/messages")))
                .andRespond(withSuccess(validResponse, MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/v1/messages")))
                .andRespond(withSuccess("{\"content\":[{\"type\":\"text\",\"text\":\"not-json\"}]}", MediaType.APPLICATION_JSON));

        LedgerExtraction result = service.parseLedgerText("Factura ACME 14500");
        assertEquals("ACME", result.counterpartyName());
        assertEquals("14500", result.amount().toPlainString());
        AiServiceException error = assertThrows(AiServiceException.class,
                () -> service.parseLedgerText("bad"));
        assertEquals(AiServiceException.Reason.INVALID_JSON, error.getReason());
        server.verify();
    }

    private AiService service(RestTemplate restTemplate) {
        AiService service = new AiService(mock(FinanceService.class), mock(InvoiceService.class), restTemplate);
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        return service;
    }

    private String jsonString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
