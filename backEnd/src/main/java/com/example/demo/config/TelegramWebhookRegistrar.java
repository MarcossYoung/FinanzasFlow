package com.example.demo.config;

import com.example.demo.service.TelegramService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class TelegramWebhookRegistrar {
    private static final Logger log = LoggerFactory.getLogger(TelegramWebhookRegistrar.class);

    private final TelegramService telegramService;
    private final String webhookUrl;
    private final String secretToken;
    private final String botToken;

    public TelegramWebhookRegistrar(
            TelegramService telegramService,
            @Value("${telegram.webhook.url:}") String webhookUrl,
            @Value("${telegram.webhook.secret-token:}") String secretToken,
            @Value("${telegram.bot.token:}") String botToken) {
        this.telegramService = telegramService;
        this.webhookUrl = webhookUrl;
        this.secretToken = secretToken;
        this.botToken = botToken;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registerWebhook() {
        if (botToken == null || botToken.isBlank()
                || webhookUrl == null || webhookUrl.isBlank()) {
            log.info("Telegram webhook registration skipped: bot token or webhook URL not configured");
            return;
        }
        try {
            telegramService.registerWebhook(webhookUrl, secretToken);
            log.info("Telegram webhook registered successfully: url={}, allowed_updates=[message,edited_message,callback_query]",
                    webhookUrl);
        } catch (Exception e) {
            log.warn("Telegram webhook registration failed (app continues normally): {}", e.getMessage(), e);
        }
    }
}
