package com.example.demo.controller;

import com.example.demo.dto.CreateTenantRequest;
import com.example.demo.dto.SetTenantActiveRequest;
import com.example.demo.dto.TenantActivityResponse;
import com.example.demo.dto.TenantOperationalSummary;
import com.example.demo.dto.UpdateTenantRequest;
import com.example.demo.service.TenantService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@PreAuthorize("hasAuthority('SUPER_ADMIN')")
@RestController
@RequestMapping("/api/operator")
public class OperatorController {
    private final TenantService tenantService;

    public OperatorController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @GetMapping("/tenants")
    @Transactional(readOnly = true)
    public ResponseEntity<List<TenantOperationalSummary>> tenants() {
        return ResponseEntity.ok(tenantService.summaries());
    }

    @GetMapping("/tenants/{id}/activity")
    public ResponseEntity<List<TenantActivityResponse>> activity(@PathVariable Long id) {
        return ResponseEntity.ok(tenantService.recentActivity(id));
    }

    @PostMapping("/tenants")
    public ResponseEntity<TenantOperationalSummary> create(@RequestBody CreateTenantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tenantService.create(request));
    }

    @PutMapping("/tenants/{id}")
    public ResponseEntity<TenantOperationalSummary> update(
            @PathVariable Long id,
            @RequestBody UpdateTenantRequest request) {
        return ResponseEntity.ok(tenantService.update(id, request));
    }

    @PutMapping("/tenants/{id}/active")
    public ResponseEntity<TenantOperationalSummary> setActive(
            @PathVariable Long id,
            @RequestBody SetTenantActiveRequest request) {
        return ResponseEntity.ok(tenantService.setActive(id, request));
    }
}
