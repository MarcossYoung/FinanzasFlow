package com.example.demo.controller;

import com.example.demo.model.AppUser;
import com.example.demo.model.AppUserRole;
import com.example.demo.model.Tenant;
import com.example.demo.repository.TenantRepo;
import com.example.demo.repository.UserRepo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:securityboundary;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "jwt.secret=security-boundary-test-secret-that-is-long-enough",
        "telegram.admin.chat-ids=",
        "app.seed.demo-data=false",
        "app.superadmin.initial-password="
})
@AutoConfigureMockMvc
class SecurityBoundaryTest {
    private static final String ADMIN_USERNAME = "boundary_admin";
    private static final String SUPER_USERNAME = "boundary_superadmin";
    private static final String MISASSIGNED_SUPER_USERNAME = "boundary_misassigned_superadmin";
    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TenantRepo tenantRepo;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Tenant tenant;

    @BeforeEach
    void setUp() {
        tenant = tenantRepo.findBySlug("security-boundary").orElseGet(() -> {
            Tenant created = new Tenant();
            created.setName("Security Boundary");
            created.setSlug("security-boundary");
            created.setCreatedAt(LocalDateTime.now());
            created.setActive(true);
            return tenantRepo.save(created);
        });
        ensureUser(ADMIN_USERNAME, AppUserRole.ADMIN, tenant);
        ensureUser(SUPER_USERNAME, AppUserRole.SUPER_ADMIN, null);
        ensureUser(MISASSIGNED_SUPER_USERNAME, AppUserRole.SUPER_ADMIN, tenant);
    }

    @Test
    void superAdminCannotAccessTenantFinancialOrAdminRoutes() throws Exception {
        String token = login(SUPER_USERNAME);

        mockMvc.perform(get("/api/costs").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/finance").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/invoices").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/customers").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/users").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void operatorTenantsExposeOnlyOperationalFields() throws Exception {
        String token = login(SUPER_USERNAME);

        mockMvc.perform(get("/api/operator/tenants").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].userCount").exists())
                .andExpect(jsonPath("$[0].actionsThisMonth").exists())
                .andExpect(jsonPath("$[0].totalOwed").doesNotExist())
                .andExpect(jsonPath("$[0].totalInvoices").doesNotExist())
                .andExpect(jsonPath("$[0].avgDSO").doesNotExist())
                .andExpect(content().string(not(containsString("precio"))))
                .andExpect(content().string(not(containsString("amount"))));
    }

    @Test
    void adminKeepsTenantRoutesButCannotAccessOperatorRoutes() throws Exception {
        String token = login(ADMIN_USERNAME);

        mockMvc.perform(get("/api/operator/tenants").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/users").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/costs").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    void tenantAdminUserListExcludesSuperAdminAccounts() throws Exception {
        String token = login(ADMIN_USERNAME);

        mockMvc.perform(get("/api/admin/users").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(ADMIN_USERNAME)))
                .andExpect(content().string(not(containsString(MISASSIGNED_SUPER_USERNAME))));
    }

    @Test
    void superAdminCanCreateTenantWithFirstAdmin() throws Exception {
        String token = login(SUPER_USERNAME);
        String username = "tenant_admin_" + System.nanoTime();

        mockMvc.perform(post("/api/operator/tenants")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Created Tenant " + username,
                                "email", "created@example.com",
                                "phone", "123",
                                "adminUsername", username,
                                "adminPassword", PASSWORD
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userCount").value(1))
                .andExpect(jsonPath("$.actionsThisMonth").value(0))
                .andExpect(jsonPath("$.totalOwed").doesNotExist());
    }

    private void ensureUser(String username, AppUserRole role, Tenant userTenant) {
        AppUser user = userRepo.findByUsername(username).orElseGet(AppUser::new);
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setAppUserRole(role);
        user.setTenant(userTenant);
        userRepo.save(user);
    }

    private String login(String username) throws Exception {
        String response = mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", PASSWORD
                        ))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return json.get("token").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
