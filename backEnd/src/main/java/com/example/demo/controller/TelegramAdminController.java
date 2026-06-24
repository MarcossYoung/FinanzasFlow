package com.example.demo.controller;

import com.example.demo.dto.ConnectCodeRequest;
import com.example.demo.dto.ConnectCodeResponse;
import com.example.demo.dto.TelegramConnectionResponse;
import com.example.demo.dto.UpdateOwnerRequest;
import com.example.demo.model.AppUser;
import com.example.demo.service.AppUserService;
import com.example.demo.service.TelegramConnectionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@PreAuthorize("hasAuthority('ADMIN')")
@RestController
@RequestMapping("/api/admin/telegram")
public class TelegramAdminController {
    private final TelegramConnectionService connectionService;
    private final AppUserService appUserService;

    public TelegramAdminController(TelegramConnectionService connectionService, AppUserService appUserService) {
        this.connectionService = connectionService;
        this.appUserService = appUserService;
    }

    @GetMapping("/connections")
    public ResponseEntity<List<TelegramConnectionResponse>> listConnections() {
        AppUser currentUser = requireCurrentTenantUser();
        return ResponseEntity.ok(connectionService.listConnections(currentUser.getTenant().getId()));
    }

    @PostMapping("/connect-codes")
    public ResponseEntity<ConnectCodeResponse> generateConnectCode(@RequestBody ConnectCodeRequest request) {
        AppUser currentUser = requireCurrentTenantUser();
        return ResponseEntity.ok(connectionService.generateConnectCode(
                currentUser.getTenant().getId(),
                request.defaultOwnerId(),
                currentUser.getId()
        ));
    }

    @DeleteMapping("/connections/{id}")
    public ResponseEntity<Void> disableConnection(@PathVariable Long id) {
        AppUser currentUser = requireCurrentTenantUser();
        connectionService.disableConnection(id, currentUser.getTenant().getId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/connections/{id}/owner")
    public ResponseEntity<TelegramConnectionResponse> updateOwner(@PathVariable Long id,
                                                                  @RequestBody UpdateOwnerRequest request) {
        AppUser currentUser = requireCurrentTenantUser();
        return ResponseEntity.ok(connectionService.updateOwner(
                id,
                currentUser.getTenant().getId(),
                request.defaultOwnerId()
        ));
    }

    private AppUser requireCurrentTenantUser() {
        AppUser currentUser = appUserService.getCurrentUser();
        if (currentUser == null || currentUser.getTenant() == null || currentUser.getTenant().getId() == null) {
            throw new IllegalStateException("Usuario sin tenant");
        }
        return currentUser;
    }
}
