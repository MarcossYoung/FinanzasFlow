package com.example.demo.dto;

import com.example.demo.model.OrderPayments;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProductPayments(
    Long id,
    String paymentType,
    BigDecimal amount,
    LocalDate paymentDate,
    String paymentMethod,
    boolean hasReceipt
) {
    public static ProductPayments from(OrderPayments op) {
        return new ProductPayments(
                op.getId(),
                op.getPaymentType(),
                op.getAmount(),
                op.getPaymentDate(),
                op.getPaymentMethod(),
                op.getReceiptPath() != null
        );
    }
}
