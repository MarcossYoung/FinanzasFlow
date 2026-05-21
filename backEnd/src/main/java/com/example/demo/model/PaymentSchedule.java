package com.example.demo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "payment_schedule")
public class PaymentSchedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    private LocalDate expectedDate;

    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(precision = 5, scale = 2)
    private BigDecimal percentage;

    @Enumerated(EnumType.STRING)
    private ScheduleStatus status;

    @jakarta.persistence.OneToMany(mappedBy = "schedule", cascade = jakarta.persistence.CascadeType.ALL)
    private List<PaymentReminder> reminders;
}
