package com.example.demo.dto;

import java.math.BigDecimal;

public record CreatePaymentRequest(
        BigDecimal valor,
        String type,
        Long product_id,
        String fecha,
        String paymentMethod
) {}
