package com.example.demo.repository;

import com.example.demo.model.TenantActivity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TenantActivityRepo extends JpaRepository<TenantActivity, Long> {
    long countByTenant_IdAndCreatedAtBetween(Long tenantId, LocalDateTime from, LocalDateTime to);

    Optional<TenantActivity> findFirstByTenant_IdOrderByCreatedAtDesc(Long tenantId);

    List<TenantActivity> findTop20ByTenant_IdOrderByCreatedAtDesc(Long tenantId);
}
