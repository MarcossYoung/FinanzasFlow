package com.example.demo.service.ingestion;

import com.example.demo.dto.LedgerExtraction;

import java.util.Optional;

public interface PendingLedgerStore {
    Optional<Long> claim(String chatId, long messageId, Long tenantId);

    void attachExtraction(long token, LedgerExtraction extraction);

    Optional<PendingLedger> get(long token);

    void remove(long token);
}
