package com.example.demo.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record FinanceDashboardResponse(
        LocalDate from,
        LocalDate to,
        BigDecimal tInc,
        BigDecimal tDep,
        BigDecimal tExp,
        BigDecimal tRev,
        List<Map<String,Object>> expenseBreakdown,
        List<Map<String,Object>> userStats,
        BigDecimal tCogs,
        BigDecimal grossProfit,
        BigDecimal netProfit,
        List<Map<String,Object>> customerStats
) {}
