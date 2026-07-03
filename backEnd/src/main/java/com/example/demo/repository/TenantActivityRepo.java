package com.example.demo.repository;

import com.example.demo.model.TenantActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TenantActivityRepo extends JpaRepository<TenantActivity, Long> {
    long countByTenant_IdAndCreatedAtBetween(Long tenantId, LocalDateTime from, LocalDateTime to);

    Optional<TenantActivity> findFirstByTenant_IdOrderByCreatedAtDesc(Long tenantId);

    List<TenantActivity> findTop20ByTenant_IdOrderByCreatedAtDesc(Long tenantId);

    @Query("""
            SELECT YEAR(a.createdAt), MONTH(a.createdAt), COUNT(a)
            FROM TenantActivity a
            WHERE a.createdAt >= :from
            GROUP BY YEAR(a.createdAt), MONTH(a.createdAt)
            ORDER BY YEAR(a.createdAt), MONTH(a.createdAt)
            """)
    List<Object[]> countByYearMonth(@Param("from") LocalDateTime from);
}
