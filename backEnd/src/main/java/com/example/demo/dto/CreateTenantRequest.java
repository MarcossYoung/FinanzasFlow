package com.example.demo.dto;

public record CreateTenantRequest(
        String name,
        String email,
        String phone,
        String adminUsername,
        String adminPassword
) {}
