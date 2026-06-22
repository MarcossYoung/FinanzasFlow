package com.example.demo.service;

import com.example.demo.config.TenantContext;
import com.example.demo.dto.CostCreateRequest;
import com.example.demo.model.*;
import com.example.demo.repository.CostRepo;
import com.example.demo.repository.TenantRepo;
import com.example.demo.repository.UserRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class CostService {
    private final CostRepo costRepo;
    private final TenantRepo tenantRepo;
    private final UserRepo userRepo;
    private final AppUserService appUserService;

    public CostService(CostRepo costRepo, TenantRepo tenantRepo, UserRepo userRepo, AppUserService appUserService) {
        this.costRepo = costRepo;
        this.tenantRepo = tenantRepo;
        this.userRepo = userRepo;
        this.appUserService = appUserService;
    }

    @Transactional
    public Costs create(CostCreateRequest request) {
        Long tenantId = TenantContext.get();
        AppUser owner = appUserService.getCurrentUser();
        if (tenantId == null || owner == null || owner.getTenant() == null
                || !tenantId.equals(owner.getTenant().getId())) {
            throw new IllegalStateException("Authenticated tenant owner is required");
        }
        return createInternal(request, owner.getTenant(), owner);
    }

    @Transactional
    public Costs createForTenant(CostCreateRequest request, Long tenantId, Long ownerId) {
        Tenant tenant = tenantRepo.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
        AppUser owner = userRepo.findByIdAndTenant_Id(ownerId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Owner does not belong to tenant"));
        return createInternal(request, tenant, owner);
    }

    private Costs createInternal(CostCreateRequest request, Tenant tenant, AppUser owner) {
        if (request == null || request.amount() == null || request.amount().signum() <= 0) {
            throw new IllegalArgumentException("Cost amount must be positive");
        }
        Costs cost = new Costs();
        cost.setDate(request.date() != null ? request.date() : LocalDate.now());
        cost.setAmount(request.amount());
        cost.setReason(request.reason());
        cost.setCostType(request.costType() != null ? request.costType() : CostType.MATERIAL);
        cost.setFrequency(request.frequency() != null ? request.frequency() : PaymentFrequency.ONE_TIME);
        cost.setCreatedAt(LocalDateTime.now());
        cost.setTenant(tenant);
        cost.setOwner(owner);
        return costRepo.save(cost);
    }
}
