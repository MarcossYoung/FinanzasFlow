package com.example.demo.repository;

import com.example.demo.model.TelegramConnectCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TelegramConnectCodeRepo extends JpaRepository<TelegramConnectCode, Long> {
    Optional<TelegramConnectCode> findByCodeHash(String codeHash);
}
