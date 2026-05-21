package com.example.demo.repository;

import com.example.demo.model.PaymentReminder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PaymentReminderRepo extends JpaRepository<PaymentReminder, Long> {
    boolean existsBySchedule_IdAndSentAtBetween(Long scheduleId, LocalDateTime from, LocalDateTime to);

    Optional<PaymentReminder> findTopByCustomer_IdOrderBySentAtDesc(Long customerId);
}
