package com.example.demo.controller;

import com.example.demo.dto.CustomerCreateRequest;
import com.example.demo.dto.CustomerResponse;
import com.example.demo.service.CustomerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {
    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    public ResponseEntity<List<CustomerResponse>> list() {
        return ResponseEntity.ok(customerService.findByTenant());
    }

    @GetMapping("/search")
    public ResponseEntity<List<CustomerResponse>> search(@RequestParam String q) {
        return ResponseEntity.ok(customerService.search(q));
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> create(@RequestBody CustomerCreateRequest req) {
        return ResponseEntity.ok(customerService.create(req));
    }

    @PostMapping("/find-or-create")
    public ResponseEntity<CustomerResponse> findOrCreate(@RequestBody CustomerCreateRequest req) {
        return ResponseEntity.ok(customerService.findOrCreate(req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CustomerResponse> update(@PathVariable Long id, @RequestBody CustomerCreateRequest req) {
        return ResponseEntity.ok(customerService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        customerService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
