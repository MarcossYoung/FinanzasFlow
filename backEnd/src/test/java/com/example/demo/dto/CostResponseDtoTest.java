package com.example.demo.dto;

import com.example.demo.model.CostType;
import com.example.demo.model.Costs;
import com.example.demo.model.PaymentFrequency;
import com.example.demo.model.Tenant;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CostResponseDtoTest {
    @Test
    void mapsScalarCostFieldsWithoutTenant() {
        Tenant tenant = new Tenant();
        tenant.setId(1L);
        LocalDate date = LocalDate.of(2026, 7, 2);
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 2, 10, 30);
        Costs cost = new Costs();
        cost.setId(7L);
        cost.setDate(date);
        cost.setAmount(new BigDecimal("123.45"));
        cost.setReason("Materials");
        cost.setCostType(CostType.MATERIAL);
        cost.setFrequency(PaymentFrequency.ONE_TIME);
        cost.setCreatedAt(createdAt);
        cost.setTenant(tenant);

        CostResponseDto dto = CostResponseDto.from(cost);

        assertEquals(7L, dto.id());
        assertEquals(date, dto.date());
        assertEquals(new BigDecimal("123.45"), dto.amount());
        assertEquals("Materials", dto.reason());
        assertEquals(CostType.MATERIAL, dto.costType());
        assertEquals(PaymentFrequency.ONE_TIME, dto.frequency());
        assertEquals(createdAt, dto.createdAt());
    }
}
