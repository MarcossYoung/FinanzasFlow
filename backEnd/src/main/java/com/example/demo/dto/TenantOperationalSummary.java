package com.example.demo.dto;

import java.time.LocalDateTime;

public record TenantOperationalSummary(
        Long id,
        String name,
        String email,
        String phone,
        boolean active,
        LocalDateTime createdAt,
        long userCount,
        long actionsThisMonth,
        LocalDateTime lastActivityAt
) {}
