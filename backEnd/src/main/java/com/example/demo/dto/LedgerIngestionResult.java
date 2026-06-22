package com.example.demo.dto;

import com.example.demo.model.LedgerDirection;
import com.example.demo.model.LedgerRecordType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LedgerIngestionResult(
        Long ingestionId,
        LedgerDirection direction,
        LedgerRecordType recordType,
        Long recordId,
        BigDecimal amount,
        String counterparty,
        LocalDate date,
        boolean alreadyCompleted
) {
}
