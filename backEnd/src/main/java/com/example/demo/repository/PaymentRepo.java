package com.example.demo.repository;

import com.example.demo.dto.MonthlyAmountRow;
import com.example.demo.model.OrderPayments;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;

public interface PaymentRepo extends JpaRepository<OrderPayments, Long> {

    List<OrderPayments> findByInvoice_Id(Long invoiceId);

    List<OrderPayments> findByPaymentDateBetween(LocalDate from, LocalDate to);

    // FIX: Standardized to use 'fecha' and 'valor'
    @Query(value = """
    SELECT to_char(date_trunc('month', p.fecha), 'YYYY-MM') AS month,
           COALESCE(SUM(p.valor), 0) AS total
    FROM pagos p
    WHERE p.fecha BETWEEN :from AND :to
    GROUP BY 1
    ORDER BY 1
    """, nativeQuery = true)
    List<MonthlyAmountRow> cashFlowByMonth(@Param("from") LocalDate from,
                                           @Param("to") LocalDate to);

    // FIX: Changed p.paymentDate and p.date to p.fecha
    @Query(value = """
        SELECT to_char(date_trunc('month', p.fecha), 'YYYY-MM') AS month,
               COALESCE(SUM(p.valor), 0) AS total
        FROM pagos p
        WHERE p.type = 'DEPOSIT'
          AND p.fecha BETWEEN :from AND :to
        GROUP BY 1
        ORDER BY 1
        """, nativeQuery = true)
    List<MonthlyAmountRow> depositsByMonth(@Param("from") LocalDate from,
                                           @Param("to") LocalDate to);

    // FIX: Changed p.paymentDate to p.fecha
    @Query(value = """
        SELECT COALESCE(SUM(p.valor), 0)
        FROM pagos p
        WHERE p.fecha BETWEEN :from AND :to
        """, nativeQuery = true)
    BigDecimal cashflowTotal(@Param("from") LocalDate from,
                             @Param("to") LocalDate to);

    List<OrderPayments> findAllByInvoice_Id(Long id);
}
