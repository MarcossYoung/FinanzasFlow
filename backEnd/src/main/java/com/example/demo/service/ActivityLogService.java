package com.example.demo.service;

import com.example.demo.model.AppUser;
import com.example.demo.model.Tenant;
import com.example.demo.model.TenantActivity;
import com.example.demo.repository.TenantActivityRepo;
import com.example.demo.repository.TenantRepo;
import com.example.demo.repository.UserRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class ActivityLogService {
    public static final String INVOICE_CREATED = "INVOICE_CREATED";
    public static final String COST_CREATED = "COST_CREATED";
    public static final String CUSTOMER_CREATED = "CUSTOMER_CREATED";
    public static final String TELEGRAM_INGESTION = "TELEGRAM_INGESTION";

    private final TenantActivityRepo activityRepo;
    private final TenantRepo tenantRepo;
    private final UserRepo userRepo;

    public ActivityLogService(TenantActivityRepo activityRepo, TenantRepo tenantRepo, UserRepo userRepo) {
        this.activityRepo = activityRepo;
        this.tenantRepo = tenantRepo;
        this.userRepo = userRepo;
    }

    @Transactional
    public void record(Long tenantId, String actionType, Long actorUserId) {
        if (tenantId == null || actionType == null || actionType.isBlank()) return;
        Tenant tenant = tenantRepo.findById(tenantId).orElse(null);
        if (tenant == null) return;
        AppUser actor = actorUserId == null ? null : userRepo.findById(actorUserId).orElse(null);

        TenantActivity activity = new TenantActivity();
        activity.setTenant(tenant);
        activity.setActionType(actionType);
        activity.setActorUser(actor);
        activity.setCreatedAt(LocalDateTime.now());
        activityRepo.save(activity);
    }
}
