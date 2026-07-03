package com.example.demo.controller;

import com.example.demo.model.AppUser;
import com.example.demo.model.AppUserRole;
import com.example.demo.model.Tenant;
import com.example.demo.model.TenantActivity;
import com.example.demo.repository.TenantActivityRepo;
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
import java.time.YearMonth;
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
    private TenantActivityRepo tenantActivityRepo;

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
        mockMvc.perform(post("/api/operator/tenants/" + tenant.getId() + "/users")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "blocked_by_admin_" + System.nanoTime(),
                                "password", PASSWORD,
                                "role", "GESTOR"
                        ))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/users/registro")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "legacy_admin_create_" + System.nanoTime(),
                                "password", PASSWORD,
                                "appUserRole", "GESTOR"
                        ))))
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

    @Test
    void superAdminCanCreateTenantUsersOnlyThroughOperatorFlow() throws Exception {
        String token = login(SUPER_USERNAME);
        String username = "operator_user_" + System.nanoTime();

        mockMvc.perform(post("/api/operator/tenants/" + tenant.getId() + "/users")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", PASSWORD,
                                "role", "GESTOR"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.appUserRole").value("GESTOR"));

        mockMvc.perform(post("/api/operator/tenants/" + tenant.getId() + "/users")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username.toUpperCase(),
                                "password", PASSWORD,
                                "role", "GESTOR"
                        ))))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/operator/tenants/" + tenant.getId() + "/users")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "bad_role_" + System.nanoTime(),
                                "password", PASSWORD,
                                "role", "SUPER_ADMIN"
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginAcceptsUsernameCaseVariationsAndReturnsLowercaseIdentity() throws Exception {
        ensureUser("case_login_user", AppUserRole.GESTOR, tenant);

        String response = mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "CASE_LOGIN_USER",
                                "password", PASSWORD
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("case_login_user"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        String token = json.get("token").asText();
        String[] tokenParts = token.split("\\.");
        String payload = new String(java.util.Base64.getUrlDecoder().decode(tokenParts[1]));
        JsonNode payloadJson = objectMapper.readTree(payload);
        org.assertj.core.api.Assertions.assertThat(payloadJson.get("sub").asText()).isEqualTo("case_login_user");
    }

    @Test
    void newTenantUserMustChangePasswordBeforeUsingApi() throws Exception {
        String superToken = login(SUPER_USERNAME);
        String username = "reset_required_" + System.nanoTime();

        String createResponse = mockMvc.perform(post("/api/operator/tenants/" + tenant.getId() + "/users")
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", PASSWORD,
                                "appUserRole", "GESTOR"
                        ))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long userId = objectMapper.readTree(createResponse).get("id").asLong();

        String loginResponse = mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", PASSWORD
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String token = objectMapper.readTree(loginResponse).get("token").asText();

        mockMvc.perform(get("/api/users/" + userId).header("Authorization", bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("PASSWORD_CHANGE_REQUIRED"));

        mockMvc.perform(post("/api/users/change-password")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", "wrong-password",
                                "newPassword", "newPassword123"
                        ))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/users/change-password")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", PASSWORD,
                                "newPassword", "short"
                        ))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/users/change-password")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", PASSWORD,
                                "newPassword", "newPassword123"
                        ))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/users/" + userId).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username));
    }

    @Test
    void adminPasswordResetForAnotherUserRequiresPasswordChange() throws Exception {
        String targetUsername = "admin_reset_target_" + System.nanoTime();
        AppUser target = ensureUser(targetUsername, AppUserRole.GESTOR, tenant);
        String adminToken = login(ADMIN_USERNAME);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                        "/api/admin/users/" + target.getId() + "/password")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("password", "temporary123"))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", targetUsername,
                                "password", "temporary123"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));
    }

    @Test
    void operatorMonthlyActivityAggregatesForSuperAdminOnly() throws Exception {
        TenantActivity activity = new TenantActivity();
        activity.setTenant(tenant);
        activity.setActionType("TEST_ACTION");
        activity.setCreatedAt(LocalDateTime.now().minusMonths(1));
        tenantActivityRepo.save(activity);
        String expectedMonth = YearMonth.from(activity.getCreatedAt()).toString();

        mockMvc.perform(get("/api/operator/activity/monthly")
                        .header("Authorization", bearer(login(SUPER_USERNAME))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"month\":\"" + expectedMonth + "\"")));

        mockMvc.perform(get("/api/operator/activity/monthly")
                        .header("Authorization", bearer(login(ADMIN_USERNAME))))
                .andExpect(status().isForbidden());
    }

    private AppUser ensureUser(String username, AppUserRole role, Tenant userTenant) {
        AppUser user = userRepo.findByUsername(username).orElseGet(AppUser::new);
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setAppUserRole(role);
        user.setTenant(userTenant);
        user.setMustChangePassword(false);
        return userRepo.save(user);
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
