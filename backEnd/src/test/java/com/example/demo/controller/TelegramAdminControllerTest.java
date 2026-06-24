package com.example.demo.controller;

import com.example.demo.dto.ConnectCodeRequest;
import com.example.demo.dto.ConnectCodeResponse;
import com.example.demo.dto.TelegramConnectionResponse;
import com.example.demo.dto.UpdateOwnerRequest;
import com.example.demo.model.AppUser;
import com.example.demo.model.Tenant;
import com.example.demo.service.AppUserService;
import com.example.demo.service.TelegramConnectionService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class TelegramAdminControllerTest {
    private final TelegramConnectionService connectionService = mock(TelegramConnectionService.class);
    private final AppUserService appUserService = mock(AppUserService.class);
    private final TelegramAdminController controller = new TelegramAdminController(connectionService, appUserService);

    @Test
    void endpointsUseCurrentTenantScope() {
        when(appUserService.getCurrentUser()).thenReturn(user(9L, 1L));
        TelegramConnectionResponse response = new TelegramConnectionResponse(
                5L, "42", 1L, 2L, "owner", 9L, "private", "Marco", true, null, null);
        when(connectionService.listConnections(1L)).thenReturn(List.of(response));
        when(connectionService.generateConnectCode(1L, 2L, 9L))
                .thenReturn(new ConnectCodeResponse("ABCD-2345", "2026-06-24T15:00"));
        when(connectionService.updateOwner(5L, 1L, 3L)).thenReturn(response);

        assertEquals(1, controller.listConnections().getBody().size());
        assertEquals("ABCD-2345", controller.generateConnectCode(new ConnectCodeRequest(2L)).getBody().code());
        controller.disableConnection(5L);
        controller.updateOwner(5L, new UpdateOwnerRequest(3L));

        verify(connectionService).listConnections(1L);
        verify(connectionService).generateConnectCode(1L, 2L, 9L);
        verify(connectionService).disableConnection(5L, 1L);
        verify(connectionService).updateOwner(5L, 1L, 3L);
    }

    @Test
    void rejectsCurrentUserWithoutTenant() {
        when(appUserService.getCurrentUser()).thenReturn(new AppUser());

        assertThrows(IllegalStateException.class, controller::listConnections);
        verifyNoInteractions(connectionService);
    }

    private AppUser user(Long userId, Long tenantId) {
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        AppUser user = new AppUser();
        user.setId(userId);
        user.setTenant(tenant);
        return user;
    }
}
