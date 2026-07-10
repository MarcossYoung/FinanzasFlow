package com.example.demo.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "tenant_ai_spend")
public class TenantAiSpend {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "period_yyyymm", nullable = false, length = 6)
    private String periodYyyymm;

    @Column(name = "spend_cents", nullable = false, precision = 12, scale = 4)
    private BigDecimal spendCents;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
