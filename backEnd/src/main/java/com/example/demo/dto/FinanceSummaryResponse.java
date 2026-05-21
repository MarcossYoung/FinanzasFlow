package com.example.demo.dto;

import java.math.BigDecimal;

public record FinanceSummaryResponse(
        BigDecimal totalIncome,
        BigDecimal totalExpenses,
        BigDecimal totalProfit,

        BigDecimal currentMonthIncome,
        BigDecimal currentMonthCashflow,
        BigDecimal currentMonthExpenses,
        BigDecimal currentMonthProfit
) {}
