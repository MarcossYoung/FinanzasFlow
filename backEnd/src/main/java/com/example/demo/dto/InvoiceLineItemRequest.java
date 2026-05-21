package com.example.demo.dto;

import java.math.BigDecimal;

public record InvoiceLineItemRequest(
        Long id,
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice
) {
}
