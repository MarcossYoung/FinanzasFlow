package com.example.demo.repository;

import com.example.demo.model.PaymentOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentOptionRepo extends JpaRepository<PaymentOption, Long> {
    List<PaymentOption> findByCategory(String category);
    boolean existsByCategory(String category);
}
