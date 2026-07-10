package com.example.demo.repository;

import com.example.demo.model.TenantAiSpend;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantAiSpendRepo extends JpaRepository<TenantAiSpend, Long> {
    Optional<TenantAiSpend> findByTenant_IdAndPeriodYyyymm(Long tenantId, String periodYyyymm);
}
