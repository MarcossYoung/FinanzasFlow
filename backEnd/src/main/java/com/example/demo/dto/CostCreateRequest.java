package com.example.demo.dto;

import com.example.demo.model.CostType;
import com.example.demo.model.PaymentFrequency;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CostCreateRequest(
        LocalDate date,
        BigDecimal amount,
        String reason,
        CostType costType,
        PaymentFrequency frequency
) {
}
