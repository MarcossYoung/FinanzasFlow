package com.example.demo.repository;

import com.example.demo.model.PaymentSchedule;
import com.example.demo.model.ScheduleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface PaymentScheduleRepo extends JpaRepository<PaymentSchedule, Long> {
    List<PaymentSchedule> findByTenant_IdAndStatusAndExpectedDateBetween(
            Long tenantId, ScheduleStatus status, LocalDate from, LocalDate to);

    List<PaymentSchedule> findByStatusAndExpectedDateBetween(
            ScheduleStatus status, LocalDate from, LocalDate to);

    List<PaymentSchedule> findByTenant_IdAndExpectedDate(Long tenantId, LocalDate expectedDate);

    @Query("""
            SELECT s FROM PaymentSchedule s
            WHERE s.invoice.id = :invoiceId
            AND s.status IN :statuses
            """)
    List<PaymentSchedule> findOpenByInvoiceId(@Param("invoiceId") Long invoiceId,
                                              @Param("statuses") List<ScheduleStatus> statuses);
}
