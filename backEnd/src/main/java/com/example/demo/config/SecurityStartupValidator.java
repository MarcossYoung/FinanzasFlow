package com.example.demo.config;

import com.example.demo.repository.TenantRepo;
import com.example.demo.repository.UserRepo;
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

    @Value("${telegram.admin.chat-ids:}")
    private String telegramAdminChatIds;

    @Value("${telegram.bot.token:}")
    private String telegramBotToken;

    @Value("${telegram.webhook.secret-token:}")
    private String telegramWebhookSecret;

    @Value("${telegram.admin.tenant-id:}")
    private String telegramTenantId;

    @Value("${telegram.admin.ingest-owner-id:}")
    private String telegramOwnerId;

    private final TenantRepo tenantRepo;
    private final UserRepo userRepo;

    public SecurityStartupValidator(TenantRepo tenantRepo, UserRepo userRepo) {
        this.tenantRepo = tenantRepo;
        this.userRepo = userRepo;
    }

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
        if (isBlank(telegramAdminChatIds)) return;
        if (isBlank(telegramBotToken) || isBlank(telegramWebhookSecret)) {
            throw new IllegalStateException("Telegram admin ingestion requires bot token and webhook secret");
        }
        Long tenantId = parseRequiredId(telegramTenantId, "TELEGRAM_ADMIN_TENANT_ID");
        Long ownerId = parseRequiredId(telegramOwnerId, "TELEGRAM_ADMIN_INGEST_OWNER_ID");
        if (!tenantRepo.existsById(tenantId)) {
            throw new IllegalStateException("Configured Telegram ingestion tenant does not exist");
        }
        if (userRepo.findByIdAndTenant_Id(ownerId, tenantId).isEmpty()) {
            throw new IllegalStateException("Configured Telegram ingestion owner does not belong to the tenant");
        }
    }

    private Long parseRequiredId(String raw, String setting) {
        if (isBlank(raw)) throw new IllegalStateException(setting + " is required for Telegram ingestion");
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(setting + " must be a numeric ID");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
