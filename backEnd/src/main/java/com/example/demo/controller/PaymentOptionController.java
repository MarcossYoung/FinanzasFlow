package com.example.demo.controller;

import com.example.demo.model.PaymentOption;
import com.example.demo.repository.PaymentOptionRepo;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payment-options")
@CrossOrigin(origins = "*")
public class PaymentOptionController {

    private final PaymentOptionRepo paymentOptionRepo;

    public PaymentOptionController(PaymentOptionRepo paymentOptionRepo) {
        this.paymentOptionRepo = paymentOptionRepo;
    }

    @GetMapping
    public ResponseEntity<List<PaymentOption>> getByCategory(@RequestParam String category) {
        return ResponseEntity.ok(paymentOptionRepo.findByCategory(category.toUpperCase()));
    }
}
