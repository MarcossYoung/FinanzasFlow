package com.example.demo.service.ingestion;

import com.example.demo.dto.LedgerExtraction;

import java.time.Instant;

public record PendingLedger(
        long token,
        String chatId,
        long messageId,
        Long tenantId,
        LedgerExtraction extraction,
        Instant createdAt
) {
    public PendingLedger withExtraction(LedgerExtraction extraction) {
        return new PendingLedger(token, chatId, messageId, tenantId, extraction, createdAt);
    }
}
