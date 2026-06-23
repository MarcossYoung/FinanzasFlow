package com.example.demo.controller;

import com.example.demo.dto.UserSummaryDto;
import com.example.demo.model.AppUser;
import com.example.demo.model.AppUserRole;
import com.example.demo.model.Status;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.example.demo.repository.UserRepo;
import com.example.demo.service.AppUserService;
import com.example.demo.service.InvoiceService;
import com.example.demo.service.WorkOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@PreAuthorize("hasAuthority('ADMIN')")
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private AppUserService appUserService;
    @Autowired
    private UserRepo userRepo;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private InvoiceService InvoiceService;

    @Autowired
    private WorkOrderService workOrderService;



    @GetMapping("/users")
    public ResponseEntity<List<UserSummaryDto>> getAllUsers(){
        AppUser currentUser = appUserService.getCurrentUser();
        Long tenantId = currentUser.getTenant().getId();
        return ResponseEntity.ok(appUserService.getUsersForTenant(tenantId));
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
            AppUser currentUser = appUserService.getCurrentUser();
            AppUserRole requestedRole = AppUserRole.valueOf(role);
            if (!canManageRole(currentUser, user, requestedRole)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            user.setAppUserRole(requestedRole);
            userRepo.save(user);
        }
        return ResponseEntity.ok(user);
    }

    @PutMapping("/users/{id}/password")
    public ResponseEntity<Void> changePassword(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        AppUser currentUser = appUserService.getCurrentUser();
        AppUser user = userRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (!canManagePassword(currentUser, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        String newPassword = body.get("password");
        if (newPassword == null || newPassword.length() < 8) {
            return ResponseEntity.badRequest().build();
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepo.save(user);
        return ResponseEntity.noContent().build();
    }

    private boolean canManagePassword(AppUser currentUser, AppUser targetUser) {
        if (currentUser == null || currentUser.getAppUserRole() == null) return false;
        if (targetUser.getAppUserRole() == AppUserRole.SUPER_ADMIN) {
            return currentUser.getAppUserRole() == AppUserRole.SUPER_ADMIN;
        }
        if (currentUser.getAppUserRole() == AppUserRole.SUPER_ADMIN) return true;
        return currentUser.getTenant() != null
                && targetUser.getTenant() != null
                && currentUser.getTenant().getId().equals(targetUser.getTenant().getId());
    }

    private boolean canManageRole(AppUser currentUser, AppUser targetUser, AppUserRole requestedRole) {
        if (currentUser == null || currentUser.getAppUserRole() == null) return false;
        if (currentUser.getAppUserRole() == AppUserRole.SUPER_ADMIN) return true;
        if (targetUser.getAppUserRole() == AppUserRole.SUPER_ADMIN || requestedRole == AppUserRole.SUPER_ADMIN) {
            return false;
        }
        return currentUser.getTenant() != null
                && targetUser.getTenant() != null
                && currentUser.getTenant().getId().equals(targetUser.getTenant().getId());
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary() {
        Map<String, Object> summary = new HashMap<>();

        // === 1. Total Users ===
        AppUser currentUser = appUserService.getCurrentUser();
        Long tenantId = currentUser.getTenant().getId();
        long totalUsers = appUserService.getUsersForTenant(tenantId).size();

        // === 2. Total Orders ===
        long totalOrders = InvoiceService.countOrders();

        // === 3. Finished Orders ===
        long finishedOrders = workOrderService.countByTypeForTenant(Status.CERRADO, tenantId);

        // === 4. Due This Week ===
        LocalDate today = LocalDate.now();
        LocalDate endOfWeek = today.plusDays(7);
        long dueThisWeek = workOrderService.countDueBetweenForTenant(tenantId, today, endOfWeek);

        // === 5. Orders by Status ===
        Map<String, Long> ordersByStatus = workOrderService.countOrdersByStatusForTenant(tenantId);


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
