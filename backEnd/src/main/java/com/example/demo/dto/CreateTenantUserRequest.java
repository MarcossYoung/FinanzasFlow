package com.example.demo.dto;

import com.example.demo.model.AppUserRole;

public record CreateTenantUserRequest(
        String username,
        String password,
        AppUserRole role
) {}
