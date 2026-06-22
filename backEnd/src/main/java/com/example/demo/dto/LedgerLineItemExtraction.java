package com.example.demo.dto;

import java.math.BigDecimal;

public record LedgerLineItemExtraction(
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice
) {
}
