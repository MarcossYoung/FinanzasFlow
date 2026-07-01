package com.example.demo.repository;

import com.example.demo.model.TelegramConnection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TelegramConnectionRepo extends JpaRepository<TelegramConnection, Long> {
    Optional<TelegramConnection> findByChatId(String chatId);

    Optional<TelegramConnection> findByChatIdAndEnabledTrue(String chatId);

    List<TelegramConnection> findByTenant_IdAndEnabledTrue(Long tenantId);

    Optional<TelegramConnection> findByIdAndTenant_Id(Long id, Long tenantId);
}
