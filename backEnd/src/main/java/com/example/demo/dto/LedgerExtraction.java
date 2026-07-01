package com.example.demo.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record LedgerExtraction(
        String titulo,
        String counterpartyName,
        String cuitDni,
        String email,
        String phone,
        BigDecimal amount,
        LocalDate issueDate,
        LocalDate dueDate,
        String description,
        List<LedgerLineItemExtraction> lineItems
) {
    public LedgerExtraction {
        lineItems = lineItems == null ? List.of() : List.copyOf(lineItems);
    }
}
