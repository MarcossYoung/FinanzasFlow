package com.example.demo.dto;

public record CustomerCreateRequest(
        String name,
        String cuitDni,
        String email,
        String phone,
        String notes,
        Integer paymentScore
) {
}
