package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity

    @Table(name = "pagos", indexes = {@Index(columnList = "fecha")})
    public class OrderPayments {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="invoice_id", nullable=false)
    @JsonIgnore
    private Invoice invoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="tenant_id")
    @JsonIgnore
    private Tenant tenant;

    @Column(name = "type")
    private String paymentType;

    @Column(name = "valor")
    private BigDecimal amount;


    @Column(name = "fecha")
    private LocalDate paymentDate;

    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(name = "receipt_path")
    private String receiptPath;






}
