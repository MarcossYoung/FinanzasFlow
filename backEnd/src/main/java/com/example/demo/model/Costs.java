package com.example.demo.model;


import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Setter
@Getter
@Entity

@Table(name = "costos", indexes = {@Index(columnList = "fecha")})
public class Costs {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "tipo")
    @Enumerated(EnumType.STRING)
    private CostType costType;

    @Column(name = "fecha")
    private LocalDate date;

    @Column(name="valor",nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name="fechacreado")
    private LocalDateTime createdAt;

    @Column(name="frequencia")
    @Enumerated(EnumType.STRING)
    private PaymentFrequency frequency;

    @Column(name="asunto")
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    @JsonIgnore
    private AppUser owner;

}
