package com.example.demo.dto;

import com.example.demo.model.CostType;
import com.example.demo.model.Costs;
import com.example.demo.model.PaymentFrequency;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record CostResponseDto(
        Long id,
        LocalDate date,
        BigDecimal amount,
        String reason,
        CostType costType,
        PaymentFrequency frequency,
        LocalDateTime createdAt
) {
    public static CostResponseDto from(Costs c) {
        return new CostResponseDto(
                c.getId(),
                c.getDate(),
                c.getAmount(),
                c.getReason(),
                c.getCostType(),
                c.getFrequency(),
                c.getCreatedAt()
        );
    }
}
