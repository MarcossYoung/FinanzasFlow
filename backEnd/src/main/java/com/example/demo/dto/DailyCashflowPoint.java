package com.example.demo.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyCashflowPoint(
        LocalDate date,
        BigDecimal inflow,
        BigDecimal outflow,
        BigDecimal net,
        BigDecimal cumulativeBalance
) {
}
