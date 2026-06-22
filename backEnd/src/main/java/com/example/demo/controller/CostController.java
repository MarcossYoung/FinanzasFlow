package com.example.demo.controller;

import com.example.demo.config.TenantContext;
import com.example.demo.dto.CostCreateRequest;
import com.example.demo.model.CostType;
import com.example.demo.model.Costs;
import com.example.demo.model.Tenant;
import com.example.demo.repository.CostRepo;
import com.example.demo.repository.TenantRepo;
import com.example.demo.service.CostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/costs")
public class CostController {

    @Autowired
    private CostRepo costRepo;

    @Autowired
    private TenantRepo tenantRepo;

    @Autowired
    private CostService costService;

    @GetMapping
    public ResponseEntity<?> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) CostType costType
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "date"));
        Long tenantId = currentTenantId();
        if (from != null && to != null) {
            LocalDate fromDate = LocalDate.parse(from);
            LocalDate toDate = LocalDate.parse(to);
            if (costType != null) {
                return ResponseEntity.ok(costRepo.findByDateBetweenAndCostTypeAndTenant_Id(
                        fromDate, toDate, costType, tenantId, pageable));
            }
            return ResponseEntity.ok(costRepo.findByDateBetweenAndTenant_Id(fromDate, toDate, tenantId, pageable));
        }
        return ResponseEntity.ok(costRepo.findByTenant_Id(tenantId, pageable));
    }

    @GetMapping("/summary")
    public ResponseEntity<?> summary(
            @RequestParam String from,
            @RequestParam String to
    ) {
        LocalDate fromDate = LocalDate.parse(from);
        LocalDate toDate = LocalDate.parse(to);
        Long tenantId = currentTenantId();
        BigDecimal total = costRepo.expensesTotal(tenantId, fromDate, toDate);
        List<Object[]> rows = costRepo.summaryByType(tenantId, fromDate, toDate);
        List<Map<String, Object>> breakdown = rows.stream()
                .map(row -> Map.<String, Object>of("costType", String.valueOf(row[0]), "total", row[1]))
                .collect(Collectors.toList());
        return ResponseEntity.ok(Map.of(
                "total", total != null ? total : BigDecimal.ZERO,
                "breakdown", breakdown
        ));
    }

    @PostMapping
    public Costs create(@RequestBody CostCreateRequest cost) {
        return costService.create(cost);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Costs> update(@PathVariable Long id, @RequestBody Costs updated) {
        Costs existing = costRepo.findByIdAndTenant_Id(id, currentTenantId()).orElse(null);
        if (existing == null) return ResponseEntity.notFound().build();
        existing.setDate(updated.getDate());
        existing.setAmount(updated.getAmount());
        existing.setReason(updated.getReason());
        existing.setCostType(updated.getCostType());
        existing.setFrequency(updated.getFrequency());
        return ResponseEntity.ok(costRepo.save(existing));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        Costs existing = costRepo.findByIdAndTenant_Id(id, currentTenantId()).orElse(null);
        if (existing == null) return ResponseEntity.notFound().build();
        costRepo.delete(existing);
        return ResponseEntity.ok().build();
    }

    private Long currentTenantId() {
        Long tenantId = TenantContext.get();
        if (tenantId == null) {
            throw new RuntimeException("Tenant not available");
        }
        return tenantId;
    }

    private Tenant currentTenant() {
        return tenantRepo.findById(currentTenantId())
                .orElseThrow(() -> new RuntimeException("Tenant not found"));
    }
}
