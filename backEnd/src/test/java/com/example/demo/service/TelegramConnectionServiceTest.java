package com.example.demo.service;

import com.example.demo.dto.ConnectCodeResponse;
import com.example.demo.model.AppUser;
import com.example.demo.model.TelegramConnectCode;
import com.example.demo.model.TelegramConnection;
import com.example.demo.model.Tenant;
import com.example.demo.repository.TelegramConnectCodeRepo;
import com.example.demo.repository.TelegramConnectionRepo;
import com.example.demo.repository.UserRepo;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TelegramConnectionServiceTest {
    private final TelegramConnectionRepo connectionRepo = mock(TelegramConnectionRepo.class);
    private final TelegramConnectCodeRepo codeRepo = mock(TelegramConnectCodeRepo.class);
    private final UserRepo userRepo = mock(UserRepo.class);
    private final TelegramConnectionService service = new TelegramConnectionService(connectionRepo, codeRepo, userRepo);

    @Test
    void generateConnectCodeStoresOnlyHash() {
        AppUser owner = user(2L, 1L);
        when(userRepo.findByIdAndTenant_Id(2L, 1L)).thenReturn(Optional.of(owner));
        when(userRepo.findByIdAndTenant_Id(3L, 1L)).thenReturn(Optional.of(user(3L, 1L)));
        when(codeRepo.findByCodeHash(anyString())).thenReturn(Optional.empty());
        when(codeRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ArgumentCaptor<TelegramConnectCode> captor = ArgumentCaptor.forClass(TelegramConnectCode.class);

        ConnectCodeResponse response = service.generateConnectCode(1L, 2L, 3L);

        assertTrue(response.code().matches("[A-Z]{4}-[0-9]{4}"));
        verify(codeRepo).save(captor.capture());
        TelegramConnectCode saved = captor.getValue();
        assertNotEquals(response.code(), saved.getCodeHash());
        assertEquals(64, saved.getCodeHash().length());
        assertEquals(owner, saved.getDefaultOwner());
        assertNull(saved.getConsumedAt());
        assertTrue(saved.getExpiresAt().isAfter(LocalDateTime.now()));
    }

    @Test
    void consumeConnectCodeCreatesPrivateConnection() {
        TelegramConnectCode code = code(1L, 2L);
        when(codeRepo.findByCodeHash(anyString())).thenReturn(Optional.of(code));
        when(connectionRepo.findByChatId("42")).thenReturn(Optional.empty());
        when(connectionRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TelegramConnection connection = service.consumeConnectCode("abcd-2345", "42", "private", "Marco");

        assertEquals("42", connection.getChatId());
        assertEquals(1L, connection.getTenant().getId());
        assertEquals(2L, connection.getDefaultOwner().getId());
        assertEquals("private", connection.getChatType());
        assertEquals("Marco", connection.getChatTitle());
        assertTrue(connection.isEnabled());
        assertNotNull(code.getConsumedAt());
    }

    @Test
    void consumeRejectsExpiredReusedInvalidAndGroupCodes() {
        TelegramConnectCode expired = code(1L, 2L);
        expired.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(codeRepo.findByCodeHash(anyString())).thenReturn(Optional.of(expired));
        assertThrows(IllegalArgumentException.class,
                () -> service.consumeConnectCode("ABCD-2345", "42", "private", "Marco"));

        TelegramConnectCode reused = code(1L, 2L);
        reused.setConsumedAt(LocalDateTime.now());
        when(codeRepo.findByCodeHash(anyString())).thenReturn(Optional.of(reused));
        assertThrows(IllegalArgumentException.class,
                () -> service.consumeConnectCode("ABCD-2345", "42", "private", "Marco"));

        when(codeRepo.findByCodeHash(anyString())).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class,
                () -> service.consumeConnectCode("ABCD-2345", "42", "private", "Marco"));

        assertThrows(IllegalArgumentException.class,
                () -> service.consumeConnectCode("ABCD-2345", "-100", "group", "Team"));
    }

    @Test
    void generateRejectsOwnerOutsideTenant() {
        when(userRepo.findByIdAndTenant_Id(2L, 1L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.generateConnectCode(1L, 2L, 3L));
        verifyNoInteractions(codeRepo);
    }

    private TelegramConnectCode code(Long tenantId, Long ownerId) {
        AppUser owner = user(ownerId, tenantId);
        TelegramConnectCode code = new TelegramConnectCode();
        code.setTenant(owner.getTenant());
        code.setDefaultOwner(owner);
        code.setExpiresAt(LocalDateTime.now().plusMinutes(15));
        code.setCreatedAt(LocalDateTime.now());
        return code;
    }

    private AppUser user(Long userId, Long tenantId) {
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        AppUser user = new AppUser();
        user.setId(userId);
        user.setTenant(tenant);
        user.setUsername("user-" + userId);
        return user;
    }
}
