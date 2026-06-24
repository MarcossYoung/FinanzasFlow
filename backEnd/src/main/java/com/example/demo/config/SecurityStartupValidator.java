package com.example.demo.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SecurityStartupValidator {
    private static final String OLD_JWT_DEFAULT = "dev-only-change-me";
    private static final String OLD_SUPERADMIN_DEFAULT = "superadmin123";

    @Value("${jwt.secret:}")
    private String jwtSecret;

    @Value("${app.superadmin.initial-password:}")
    private String superadminInitialPassword;

    @Value("${telegram.bot.token:}")
    private String telegramBotToken;

    @Value("${telegram.webhook.secret-token:}")
    private String telegramWebhookSecret;

    @PostConstruct
    public void validate() {
        if (isBlank(jwtSecret) || OLD_JWT_DEFAULT.equals(jwtSecret)) {
            throw new IllegalStateException("JWT_SECRET must be set to a strong, non-default value");
        }
        if (OLD_SUPERADMIN_DEFAULT.equals(superadminInitialPassword)) {
            throw new IllegalStateException("APP_SUPERADMIN_INITIAL_PASSWORD cannot use the old public default");
        }
        validateTelegramIngestion();
    }

    private void validateTelegramIngestion() {
        if (isBlank(telegramBotToken) && isBlank(telegramWebhookSecret)) {
            return;
        }
        if (isBlank(telegramBotToken) || isBlank(telegramWebhookSecret)) {
            throw new IllegalStateException("Telegram webhook requires bot token and webhook secret");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
