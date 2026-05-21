package com.example.demo.dto;

import java.math.BigDecimal;

public interface MonthlyAmountRow {
    String getMonth();    // Maps to 'AS month'
    BigDecimal getTotal(); // Maps to 'AS total'}
}
