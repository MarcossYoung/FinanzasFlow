package com.example.demo.dto;

import com.example.demo.model.TenantActivity;

import java.time.LocalDateTime;

public record TenantActivityResponse(Long id, String actionType, LocalDateTime createdAt) {
    public static TenantActivityResponse from(TenantActivity activity) {
        return new TenantActivityResponse(
                activity.getId(),
                activity.getActionType(),
                activity.getCreatedAt()
        );
    }
}
