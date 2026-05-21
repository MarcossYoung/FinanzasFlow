package com.example.demo.controller;

import com.example.demo.model.AppUser;
import com.example.demo.model.Status;
import com.example.demo.repository.UserRepo;
import com.example.demo.service.AppUserService;
import com.example.demo.service.InvoiceService;
import com.example.demo.service.WorkOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "http://localhost:3000")
public class AdminController {

    @Autowired
    private AppUserService appUserService;
    @Autowired
    private UserRepo userRepo;

    @Autowired
    private InvoiceService InvoiceService;

    @Autowired
    private WorkOrderService workOrderService;



    @GetMapping("/users")
    public ResponseEntity<List<AppUser>> getAllUsers(){
        return ResponseEntity.ok( userRepo.findAll());
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        if (appUserService.delete(id)) {
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/users/{id}/role")
    public ResponseEntity<AppUser> updateUserRole(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        AppUser user = userRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        String role = body.get("appUserRole");
        if (role != null && !role.isBlank()) {
            user.setAppUserRole(com.example.demo.model.AppUserRole.valueOf(role));
            userRepo.save(user);
        }
        return ResponseEntity.ok(user);
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary() {
        Map<String, Object> summary = new HashMap<>();

        // === 1. Total Users ===
        long totalUsers = appUserService.countUsers();

        // === 2. Total Orders ===
        long totalOrders = InvoiceService.countOrders();

        // === 3. Finished Orders ===
        long finishedOrders = workOrderService.countByType(Status.CERRADO);

        // === 4. Due This Week ===
        LocalDate today = LocalDate.now();
        LocalDate endOfWeek = today.plusDays(7);
        long dueThisWeek = workOrderService.countDueBetween(today, endOfWeek);

        // === 5. Orders by Status ===
        Map<String, Long> ordersByStatus = workOrderService.countOrdersByStatus();


        // === 7. Top Products ===
        List<Object[]> topProducts = InvoiceService.findTopProducts();

        summary.put("totalUsers", totalUsers);
        summary.put("totalOrders", totalOrders);
        summary.put("finishedOrders", finishedOrders);
        summary.put("dueThisWeek", dueThisWeek);
        summary.put("ordersByStatus", ordersByStatus);
        summary.put("topProducts", topProducts);

        return ResponseEntity.ok(summary);
    }
}
