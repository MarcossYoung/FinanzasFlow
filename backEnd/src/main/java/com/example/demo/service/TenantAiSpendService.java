package com.example.demo.service;

import com.example.demo.dto.AiUsage;
import com.example.demo.exceptions.AiSpendLimitExceededException;
import com.example.demo.model.TenantAiSpend;
import com.example.demo.repository.TenantAiSpendRepo;
import com.example.demo.repository.TenantRepo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

@Service
public class TenantAiSpendService {
    private static final BigDecimal INPUT_CENTS_PER_TOKEN = new BigDecimal("0.0001");
    private static final BigDecimal OUTPUT_CENTS_PER_TOKEN = new BigDecimal("0.0005");

    private final TenantAiSpendRepo repo;
    private final TenantRepo tenantRepo;

    @Value("${ledger.extract.monthly-spend-limit-cents:700}")
    private int limitCents;

    public TenantAiSpendService(TenantAiSpendRepo repo, TenantRepo tenantRepo) {
        this.repo = repo;
        this.tenantRepo = tenantRepo;
    }

    private String currentPeriod() {
        return YearMonth.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
    }

    public BigDecimal costCentsFor(AiUsage usage) {
        return BigDecimal.valueOf(usage.inputTokens())
                .multiply(INPUT_CENTS_PER_TOKEN)
                .add(BigDecimal.valueOf(usage.outputTokens()).multiply(OUTPUT_CENTS_PER_TOKEN))
                .setScale(4, RoundingMode.HALF_UP);
    }

    public void assertUnderLimit(Long tenantId) {
        repo.findByTenant_IdAndPeriodYyyymm(tenantId, currentPeriod())
                .filter(spend -> spend.getSpendCents().compareTo(BigDecimal.valueOf(limitCents)) >= 0)
                .ifPresent(spend -> {
                    throw new AiSpendLimitExceededException("Se alcanzo el limite de uso de IA para este mes.");
                });
    }

    @Transactional
    public void recordSpend(Long tenantId, BigDecimal costCents) {
        String period = currentPeriod();
        repo.findByTenant_IdAndPeriodYyyymm(tenantId, period)
                .ifPresentOrElse(
                        spend -> addSpend(spend, costCents),
                        () -> createSpendWithRetry(tenantId, period, costCents)
                );
    }

    private void createSpendWithRetry(Long tenantId, String period, BigDecimal costCents) {
        try {
            TenantAiSpend spend = new TenantAiSpend();
            spend.setTenant(tenantRepo.getReferenceById(tenantId));
            spend.setPeriodYyyymm(period);
            spend.setSpendCents(costCents);
            spend.setUpdatedAt(LocalDateTime.now());
            repo.save(spend);
        } catch (DataIntegrityViolationException e) {
            // Concurrent first call for this tenant/month inserted the row first; re-read once and update it.
            TenantAiSpend spend = repo.findByTenant_IdAndPeriodYyyymm(tenantId, period).orElseThrow(() -> e);
            addSpend(spend, costCents);
        }
    }

    private void addSpend(TenantAiSpend spend, BigDecimal costCents) {
        spend.setSpendCents(spend.getSpendCents().add(costCents));
        spend.setUpdatedAt(LocalDateTime.now());
        repo.save(spend);
    }
}
