package com.example.demo.service;

import com.example.demo.dto.CreateTenantRequest;
import com.example.demo.dto.CreateTenantUserRequest;
import com.example.demo.dto.SetTenantActiveRequest;
import com.example.demo.dto.TenantActivityResponse;
import com.example.demo.dto.TenantOperationalSummary;
import com.example.demo.dto.UpdateTenantRequest;
import com.example.demo.dto.UserSummaryDto;
import com.example.demo.model.AppUser;
import com.example.demo.model.AppUserRole;
import com.example.demo.model.Tenant;
import com.example.demo.repository.TenantActivityRepo;
import com.example.demo.repository.TenantRepo;
import com.example.demo.repository.UserRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class TenantService {
    private final TenantRepo tenantRepo;
    private final UserRepo userRepo;
    private final TenantActivityRepo activityRepo;
    private final AppUserService appUserService;

    public TenantService(TenantRepo tenantRepo,
                         UserRepo userRepo,
                         TenantActivityRepo activityRepo,
                         AppUserService appUserService) {
        this.tenantRepo = tenantRepo;
        this.userRepo = userRepo;
        this.activityRepo = activityRepo;
        this.appUserService = appUserService;
    }

    @Transactional(readOnly = true)
    public List<TenantOperationalSummary> summaries() {
        LocalDate today = LocalDate.now();
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime nextMonthStart = today.plusMonths(1).withDayOfMonth(1).atStartOfDay();

        return tenantRepo.findAll().stream()
                .map(tenant -> new TenantOperationalSummary(
                        tenant.getId(),
                        tenant.getName(),
                        tenant.getEmail(),
                        tenant.getPhone(),
                        tenant.isActive(),
                        tenant.getCreatedAt(),
                        userRepo.countByTenant_IdAndAppUserRoleNot(tenant.getId(), AppUserRole.SUPER_ADMIN),
                        activityRepo.countByTenant_IdAndCreatedAtBetween(tenant.getId(), monthStart, nextMonthStart),
                        activityRepo.findFirstByTenant_IdOrderByCreatedAtDesc(tenant.getId())
                                .map(activity -> activity.getCreatedAt())
                                .orElse(null)
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TenantActivityResponse> recentActivity(Long tenantId) {
        ensureTenant(tenantId);
        return activityRepo.findTop20ByTenant_IdOrderByCreatedAtDesc(tenantId).stream()
                .map(TenantActivityResponse::from)
                .toList();
    }

    @Transactional
    public TenantOperationalSummary create(CreateTenantRequest request) {
        validateCreate(request);
        Tenant tenant = new Tenant();
        tenant.setName(request.name().trim());
        tenant.setSlug(uniqueSlug(request.name()));
        tenant.setEmail(trimToNull(request.email()));
        tenant.setPhone(trimToNull(request.phone()));
        tenant.setCreatedAt(LocalDateTime.now());
        tenant.setActive(true);
        Tenant saved = tenantRepo.save(tenant);
        appUserService.createTenantUser(saved, request.adminUsername(), request.adminPassword(), AppUserRole.ADMIN);
        return summary(saved.getId());
    }

    @Transactional
    public TenantOperationalSummary update(Long tenantId, UpdateTenantRequest request) {
        Tenant tenant = ensureTenant(tenantId);
        if (request.name() != null && !request.name().isBlank()) {
            tenant.setName(request.name().trim());
        }
        tenant.setEmail(trimToNull(request.email()));
        tenant.setPhone(trimToNull(request.phone()));
        return summary(tenant.getId());
    }

    @Transactional
    public TenantOperationalSummary setActive(Long tenantId, SetTenantActiveRequest request) {
        Tenant tenant = ensureTenant(tenantId);
        tenant.setActive(request.active());
        return summary(tenant.getId());
    }

    @Transactional
    public UserSummaryDto addUserToTenant(Long tenantId, CreateTenantUserRequest request) {
        Tenant tenant = ensureTenant(tenantId);
        AppUser created = appUserService.createTenantUser(
                tenant,
                request.username(),
                request.password(),
                request.role()
        );
        return new UserSummaryDto(created.getId(), created.getUsername(), created.getAppUserRole().name());
    }

    @Transactional(readOnly = true)
    public TenantOperationalSummary summary(Long tenantId) {
        return summaries().stream()
                .filter(summary -> summary.id().equals(tenantId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
    }

    private Tenant ensureTenant(Long tenantId) {
        return tenantRepo.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
    }

    private void validateCreate(CreateTenantRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Tenant name is required");
        }
        if (request.adminUsername() == null || request.adminUsername().isBlank()) {
            throw new IllegalArgumentException("Admin username is required");
        }
        if (request.adminPassword() == null || request.adminPassword().length() < 8) {
            throw new IllegalArgumentException("Admin password must be at least 8 characters");
        }
    }

    private String uniqueSlug(String name) {
        String base = slugify(name);
        String candidate = base;
        int suffix = 2;
        while (tenantRepo.existsBySlug(candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private String slugify(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return normalized.isBlank() ? "tenant" : normalized;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
