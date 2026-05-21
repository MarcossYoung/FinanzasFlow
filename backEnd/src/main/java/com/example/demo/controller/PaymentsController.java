package com.example.demo.controller;

import com.example.demo.dto.CreatePaymentRequest;
import com.example.demo.dto.ProductPayments;
import com.example.demo.exceptions.ResourceNotFoundException;
import com.example.demo.model.OrderPayments;
import com.example.demo.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@CrossOrigin(origins = "*")
public class PaymentsController {
    @Autowired
    private PaymentService paymentService;

    @GetMapping("/{id}")
    public ResponseEntity<List<ProductPayments>> getPayments(@PathVariable Long id) throws ResourceNotFoundException {
        List<ProductPayments> payments = paymentService.getPayments(id);
        if (payments == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(payments);
    }

    @PostMapping
    public ResponseEntity<OrderPayments> createPayment(@RequestBody CreatePaymentRequest req) {
        OrderPayments p = paymentService.createPayment(req);
        return ResponseEntity.ok(p);
    }

    @PostMapping("/{id}/receipt")
    public ResponseEntity<Map<String, String>> uploadReceipt(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        try {
            paymentService.uploadReceipt(id, file);
            return ResponseEntity.ok(Map.of("message", "Receipt uploaded successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to save receipt"));
        }
    }

    @GetMapping("/{id}/receipt")
    public ResponseEntity<Void> getReceipt(@PathVariable Long id) {
        try {
            String url = paymentService.getReceiptUrl(id);
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header("Location", url)
                    .build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
