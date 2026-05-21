package com.example.demo.dto;

import com.example.demo.model.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record InvoiceCreateRequest(
        Long id,
        String titulo,
        Long cantidad,
        LocalDate startDate,
        LocalDate fechaEntrega,
        LocalDate fechaEstimada,
        String foto,
        String notas,
        BigDecimal precio,
        PaymentStatus status,
        BigDecimal amount,
        String clientPhone,
        Long customerId,
        List<InvoiceLineItemRequest> lineItems
) {
}
