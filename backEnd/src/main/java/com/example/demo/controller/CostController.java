package com.example.demo.controller;

import com.example.demo.model.CostType;
import com.example.demo.model.Costs;
import com.example.demo.repository.CostRepo;
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
@CrossOrigin(origins = "*")
public class CostController {

    @Autowired
    private CostRepo costRepo;

    @GetMapping
    public ResponseEntity<?> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) CostType costType
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "date"));
        if (from != null && to != null) {
            LocalDate fromDate = LocalDate.parse(from);
            LocalDate toDate = LocalDate.parse(to);
            if (costType != null) {
                return ResponseEntity.ok(costRepo.findByDateBetweenAndCostType(fromDate, toDate, costType, pageable));
            }
            return ResponseEntity.ok(costRepo.findByDateBetween(fromDate, toDate, pageable));
        }
        return ResponseEntity.ok(costRepo.findAll(pageable));
    }

    @GetMapping("/summary")
    public ResponseEntity<?> summary(
            @RequestParam String from,
            @RequestParam String to
    ) {
        LocalDate fromDate = LocalDate.parse(from);
        LocalDate toDate = LocalDate.parse(to);
        BigDecimal total = costRepo.expensesTotal(fromDate, toDate);
        List<Object[]> rows = costRepo.summaryByType(fromDate, toDate);
        List<Map<String, Object>> breakdown = rows.stream()
                .map(row -> Map.<String, Object>of("costType", String.valueOf(row[0]), "total", row[1]))
                .collect(Collectors.toList());
        return ResponseEntity.ok(Map.of(
                "total", total != null ? total : BigDecimal.ZERO,
                "breakdown", breakdown
        ));
    }

    @PostMapping
    public Costs create(@RequestBody Costs cost) {
        if (cost.getCreatedAt() == null) cost.setCreatedAt(LocalDateTime.now());
        return costRepo.save(cost);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Costs> update(@PathVariable Long id, @RequestBody Costs updated) {
        Costs existing = costRepo.findById(id).orElse(null);
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
        costRepo.deleteById(id);
        return ResponseEntity.ok().build();
    }
}
