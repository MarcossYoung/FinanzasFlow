package com.example.demo.repository;

import com.example.demo.dto.MonthlyAmountRow;
import com.example.demo.model.CostType;
import com.example.demo.model.Costs;
import com.example.demo.model.PaymentFrequency;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface CostRepo extends JpaRepository<Costs, Long> {

    List<Costs> findByDateBetween(LocalDate from, LocalDate to);

    Page<Costs> findByDateBetween(LocalDate from, LocalDate to, Pageable pageable);

    Page<Costs> findByDateBetweenAndCostType(LocalDate from, LocalDate to, CostType costType, Pageable pageable);

    boolean existsByDateAndReasonAndAmount(LocalDate date, String reason, BigDecimal amount);

    // This query is used by FinanceService.dashboard()
    // Using 'valor' and 'fecha' to match your @Column annotations exactly
    @Query(value = """
        SELECT to_char(date_trunc('month', c.fecha), 'YYYY-MM') AS month,
               COALESCE(SUM(c.valor), 0) AS total
        FROM costos c
        WHERE c.fecha BETWEEN :from AND :to
        GROUP BY 1
        ORDER BY 1
        """, nativeQuery = true)
    List<MonthlyAmountRow> expensesByDate(@Param("from") LocalDate from,
                                          @Param("to") LocalDate to);

    @Query(value = "SELECT COALESCE(SUM(c.valor), 0) FROM costos c WHERE c.fecha BETWEEN :from AND :to",
            nativeQuery = true)
    BigDecimal expensesTotal(@Param("from") LocalDate from,
                             @Param("to") LocalDate to);

    // Used for the Type breakdown chart
    @Query(value = """
        SELECT to_char(date_trunc('month', c.fecha), 'YYYY-MM') AS month,
               COALESCE(SUM(c.valor), 0) AS total
        FROM costos c
        WHERE c.tipo = :type
          AND c.fecha BETWEEN :from AND :to
        GROUP BY 1
        ORDER BY 1
        """, nativeQuery = true)
    List<MonthlyAmountRow> expensesByMonthForCostTypeRaw(@Param("type") String type,
                                                         @Param("from") LocalDate from,
                                                         @Param("to") LocalDate to);

    List<Costs> findByFrequencyNot(PaymentFrequency frequency);

    @Query(value = "SELECT c.tipo AS costType, COALESCE(SUM(c.valor), 0) AS total " +
                   "FROM costos c WHERE c.fecha BETWEEN :from AND :to GROUP BY c.tipo",
           nativeQuery = true)
    List<Object[]> summaryByType(@Param("from") LocalDate from, @Param("to") LocalDate to);
}