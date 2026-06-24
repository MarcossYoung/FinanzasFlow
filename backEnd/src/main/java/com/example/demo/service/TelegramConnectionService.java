package com.example.demo.service;

import com.example.demo.dto.ConnectCodeResponse;
import com.example.demo.dto.TelegramConnectionResponse;
import com.example.demo.model.AppUser;
import com.example.demo.model.TelegramConnectCode;
import com.example.demo.model.TelegramConnection;
import com.example.demo.repository.TelegramConnectCodeRepo;
import com.example.demo.repository.TelegramConnectionRepo;
import com.example.demo.repository.UserRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class TelegramConnectionService {
    private static final String LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String DIGITS = "23456789";
    private static final int CODE_TTL_MINUTES = 15;

    private final TelegramConnectionRepo connectionRepo;
    private final TelegramConnectCodeRepo codeRepo;
    private final UserRepo userRepo;
    private final SecureRandom random = new SecureRandom();

    public TelegramConnectionService(TelegramConnectionRepo connectionRepo,
                                     TelegramConnectCodeRepo codeRepo,
                                     UserRepo userRepo) {
        this.connectionRepo = connectionRepo;
        this.codeRepo = codeRepo;
        this.userRepo = userRepo;
    }

    @Transactional
    public ConnectCodeResponse generateConnectCode(Long tenantId, Long defaultOwnerId, Long createdByUserId) {
        AppUser owner = userRepo.findByIdAndTenant_Id(defaultOwnerId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("El usuario elegido no pertenece a este tenant"));
        AppUser creator = createdByUserId == null ? null : userRepo.findByIdAndTenant_Id(createdByUserId, tenantId).orElse(null);
        LocalDateTime now = LocalDateTime.now();
        String code = uniquePlainCode();

        TelegramConnectCode connectCode = new TelegramConnectCode();
        connectCode.setCodeHash(hashCode(code));
        connectCode.setTenant(owner.getTenant());
        connectCode.setDefaultOwner(owner);
        connectCode.setCreatedByUser(creator);
        connectCode.setExpiresAt(now.plusMinutes(CODE_TTL_MINUTES));
        connectCode.setCreatedAt(now);
        codeRepo.save(connectCode);

        return new ConnectCodeResponse(code, connectCode.getExpiresAt().toString());
    }

    @Transactional
    public TelegramConnection consumeConnectCode(String plainCode, String chatId, String chatType, String chatTitle) {
        if (!"private".equalsIgnoreCase(safe(chatType))) {
            throw new IllegalArgumentException("Conecta Telegram desde un chat privado con el bot.");
        }
        if (isBlank(chatId)) {
            throw new IllegalArgumentException("No pude identificar el chat de Telegram.");
        }
        String normalizedCode = normalizeCode(plainCode);
        TelegramConnectCode code = codeRepo.findByCodeHash(hashCode(normalizedCode))
                .orElseThrow(() -> new IllegalArgumentException("Codigo invalido. Genera uno nuevo desde Admin."));
        LocalDateTime now = LocalDateTime.now();
        if (code.getConsumedAt() != null) {
            throw new IllegalArgumentException("Este codigo ya fue usado. Genera uno nuevo desde Admin.");
        }
        if (code.getExpiresAt().isBefore(now)) {
            throw new IllegalArgumentException("Este codigo expiro. Genera uno nuevo desde Admin.");
        }

        TelegramConnection connection = connectionRepo.findByChatId(chatId).orElseGet(TelegramConnection::new);
        if (connection.getId() == null) {
            connection.setChatId(chatId);
            connection.setCreatedAt(now);
        }
        connection.setTenant(code.getTenant());
        connection.setDefaultOwner(code.getDefaultOwner());
        connection.setConnectedByUser(code.getCreatedByUser());
        connection.setChatType(trimToLength(chatType, 40));
        connection.setChatTitle(trimToLength(chatTitle, 255));
        connection.setEnabled(true);
        connection.setUpdatedAt(now);
        code.setConsumedAt(now);
        return connectionRepo.save(connection);
    }

    @Transactional(readOnly = true)
    public Optional<TelegramConnection> resolveConnection(String chatId) {
        if (isBlank(chatId)) return Optional.empty();
        return connectionRepo.findByChatIdAndEnabledTrue(chatId);
    }

    @Transactional(readOnly = true)
    public List<TelegramConnectionResponse> listConnections(Long tenantId) {
        return connectionRepo.findByTenant_IdAndEnabledTrue(tenantId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void disableConnection(Long id, Long tenantId) {
        TelegramConnection connection = connectionRepo.findByIdAndTenant_Id(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Conexion no encontrada"));
        connection.setEnabled(false);
        connection.setUpdatedAt(LocalDateTime.now());
    }

    @Transactional
    public TelegramConnectionResponse updateOwner(Long id, Long tenantId, Long ownerId) {
        TelegramConnection connection = connectionRepo.findByIdAndTenant_Id(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Conexion no encontrada"));
        AppUser owner = userRepo.findByIdAndTenant_Id(ownerId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("El usuario elegido no pertenece a este tenant"));
        connection.setDefaultOwner(owner);
        connection.setUpdatedAt(LocalDateTime.now());
        return toResponse(connection);
    }

    public TelegramConnectionResponse toResponse(TelegramConnection connection) {
        AppUser owner = connection.getDefaultOwner();
        AppUser connectedBy = connection.getConnectedByUser();
        return new TelegramConnectionResponse(
                connection.getId(),
                connection.getChatId(),
                connection.getTenant() == null ? null : connection.getTenant().getId(),
                owner == null ? null : owner.getId(),
                owner == null ? null : owner.getUsername(),
                connectedBy == null ? null : connectedBy.getId(),
                connection.getChatType(),
                connection.getChatTitle(),
                connection.isEnabled(),
                connection.getCreatedAt() == null ? null : connection.getCreatedAt().toString(),
                connection.getUpdatedAt() == null ? null : connection.getUpdatedAt().toString()
        );
    }

    private String uniquePlainCode() {
        for (int attempts = 0; attempts < 10; attempts++) {
            String code = randomCode();
            if (codeRepo.findByCodeHash(hashCode(code)).isEmpty()) {
                return code;
            }
        }
        throw new IllegalStateException("No pude generar un codigo unico");
    }

    private String randomCode() {
        StringBuilder builder = new StringBuilder(9);
        for (int i = 0; i < 4; i++) {
            builder.append(LETTERS.charAt(random.nextInt(LETTERS.length())));
        }
        builder.append("-");
        for (int i = 0; i < 4; i++) {
            builder.append(DIGITS.charAt(random.nextInt(DIGITS.length())));
        }
        return builder.toString();
    }

    private String hashCode(String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(normalizeCode(code).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("No pude proteger el codigo de conexion", e);
        }
    }

    private String normalizeCode(String code) {
        if (isBlank(code)) {
            throw new IllegalArgumentException("Codigo requerido.");
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private String trimToLength(String value, int maxLength) {
        if (isBlank(value)) return null;
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
