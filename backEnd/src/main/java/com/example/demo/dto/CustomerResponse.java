package com.example.demo.dto;

import com.example.demo.model.Customer;

public record CustomerResponse(
        Long id,
        Long tenantId,
        String name,
        String cuitDni,
        String email,
        String phone,
        String notes,
        Integer paymentScore
) {
    public static CustomerResponse from(Customer c) {
        return new CustomerResponse(
                c.getId(),
                c.getTenant() != null ? c.getTenant().getId() : null,
                c.getName(),
                c.getCuitDni(),
                c.getEmail(),
                c.getPhone(),
                c.getNotes(),
                c.getPaymentScore()
        );
    }
}
