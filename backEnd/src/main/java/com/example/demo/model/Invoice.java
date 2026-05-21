package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Setter
@Getter
@Entity
@Table(name = "invoices", indexes = {
    @Index(columnList = "startdate"),
    @Index(columnList = "type")
})
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    @JsonIgnore
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    @JsonIgnore
    private Customer customer;

    @Column(name = "titulo")
    private String titulo;

    @Column(name = "cantidad")
    private long cantidad;

    @Column(name = "startdate")
    private LocalDate startDate;

    @Column(name = "fechaentrega")
    private LocalDate fechaEntrega;

    @Column(name = "fechaestimada")
    private LocalDate fechaEstimada;

    @Column(name = "foto")
    private String foto;

    @Column(name = "notas")
    private String notas;

    @Column(name = "precio", nullable = false, precision = 12, scale = 2)
    private BigDecimal precio;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    private List<InvoiceLineItem> lineItems = new ArrayList<>();

    @OneToOne(mappedBy = "invoice", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    private WorkOrder workOrder;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<OrderPayments> orderPayments;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<PaymentSchedule> paymentSchedules;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ownerid", referencedColumnName = "id")
    @JsonIgnore
    private AppUser owner;

    @Column(name = "pagostatus")
    @Enumerated(EnumType.STRING)
    private PaymentStatus pagoStatus;

    @Column(name = "client_email")
    private String clientPhone;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;
}
