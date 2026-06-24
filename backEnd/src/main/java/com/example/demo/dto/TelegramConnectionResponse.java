package com.example.demo.dto;

public record TelegramConnectionResponse(
        Long id,
        String chatId,
        Long tenantId,
        Long defaultOwnerId,
        String defaultOwnerUsername,
        Long connectedByUserId,
        String chatType,
        String chatTitle,
        boolean enabled,
        String createdAt,
        String updatedAt
) {}
