package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;


@Entity
@Getter
@Setter
@Table(name = "work_orders", indexes = {@Index(columnList = "status")})
public class WorkOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    @JsonBackReference
    private Invoice invoice;

    @Enumerated(EnumType.STRING )
    @Column(name="status")
    private Status status;

    private LocalDateTime updateAt;




}
