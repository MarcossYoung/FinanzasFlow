package com.example.demo.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "payment_options")
public class PaymentOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category", nullable = false)
    private String category; // "TYPE" or "METHOD"

    @Column(name = "code", nullable = false)
    private String code; // "DEPOSIT", "CASH", etc.

    @Column(name = "label", nullable = false)
    private String label; // "Seña", "Efectivo", etc.

    public PaymentOption(String category, String code, String label) {
        this.category = category;
        this.code = code;
        this.label = label;
    }
}
