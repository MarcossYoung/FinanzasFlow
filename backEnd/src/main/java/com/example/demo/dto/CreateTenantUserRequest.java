package com.example.demo.dto;

import com.example.demo.model.AppUserRole;
import com.fasterxml.jackson.annotation.JsonAlias;

public record CreateTenantUserRequest(
        String username,
        String password,
        @JsonAlias("role")
        AppUserRole appUserRole
) {}
