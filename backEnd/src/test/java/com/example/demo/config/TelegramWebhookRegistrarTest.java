package com.example.demo.config;

import com.example.demo.service.TelegramService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TelegramWebhookRegistrarTest {
    @Test
    void skipsWhenBotTokenIsBlank() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        TelegramWebhookRegistrar registrar = registrar(restTemplate, "https://example.com/webhook", "test-secret", "");
        server.expect(never(), requestTo(containsString("api.telegram.org")));

        registrar.registerWebhook();

        server.verify();
    }

    @Test
    void skipsWhenWebhookUrlIsBlank() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        TelegramWebhookRegistrar registrar = registrar(restTemplate, "", "test-secret", "test-token");
        server.expect(never(), requestTo(containsString("api.telegram.org")));

        registrar.registerWebhook();

        server.verify();
    }

    @Test
    void callsSetWebhookWithCallbackQueryWhenConfigured() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        TelegramWebhookRegistrar registrar = registrar(
                restTemplate, "https://example.com/webhook", "test-secret", "test-token");
        server.expect(requestTo(containsString("test-token/setWebhook")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("callback_query")))
                .andExpect(content().string(containsString("https://example.com/webhook")))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        registrar.registerWebhook();

        server.verify();
    }

    private TelegramWebhookRegistrar registrar(RestTemplate restTemplate, String webhookUrl,
                                               String secretToken, String botToken) {
        TelegramService telegramService = new TelegramService(restTemplate);
        ReflectionTestUtils.setField(telegramService, "token", botToken);
        return new TelegramWebhookRegistrar(telegramService, webhookUrl, secretToken, botToken);
    }
}
