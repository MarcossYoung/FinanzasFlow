package com.example.demo.dto;

import com.example.demo.model.InvoiceLineItem;

import java.math.BigDecimal;

public record InvoiceLineItemResponse(
        Long id,
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal
) {
    public static InvoiceLineItemResponse from(InvoiceLineItem item) {
        return new InvoiceLineItemResponse(
                item.getId(),
                item.getDescription(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getSubtotal()
        );
    }
}
