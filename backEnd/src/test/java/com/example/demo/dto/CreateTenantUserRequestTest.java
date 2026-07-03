package com.example.demo.dto;

import com.example.demo.model.AppUserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CreateTenantUserRequestTest {
    @Test
    void deserializesAppUserRoleField() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        CreateTenantUserRequest request = mapper.readValue(
                "{\"username\":\"u\",\"password\":\"p\",\"appUserRole\":\"GESTOR\"}",
                CreateTenantUserRequest.class);

        assertEquals("u", request.username());
        assertEquals(AppUserRole.GESTOR, request.appUserRole());
    }
}
