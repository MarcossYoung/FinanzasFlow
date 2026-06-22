package com.example.demo.repository;

import com.example.demo.model.TelegramIngestionStatus;
import com.example.demo.model.TelegramLedgerIngestion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface TelegramLedgerIngestionRepo extends JpaRepository<TelegramLedgerIngestion, Long> {
    Optional<TelegramLedgerIngestion> findByChatIdAndMessageId(String chatId, Long messageId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM TelegramLedgerIngestion i WHERE i.id = :id")
    Optional<TelegramLedgerIngestion> findLockedById(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE TelegramLedgerIngestion i
            SET i.updatedAt = :now, i.failureReason = null
            WHERE i.id = :id AND i.status = :status AND i.updatedAt < :staleBefore
            """)
    int reclaimIfStale(@Param("id") Long id,
                       @Param("status") TelegramIngestionStatus status,
                       @Param("staleBefore") LocalDateTime staleBefore,
                       @Param("now") LocalDateTime now);
}
