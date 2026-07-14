package com.example.demo.repository;

import com.example.demo.dto.MonthlyAmountRow;
import com.example.demo.model.Invoice;
import com.example.demo.model.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public interface InvoiceRepo extends JpaRepository<Invoice, Long> {

    @Query(value = """
    SELECT to_char(date_trunc('month', p.startdate), 'YYYY-MM') AS month,
           COALESCE(SUM(p.precio), 0) AS total
    FROM invoices p
    WHERE p.startdate BETWEEN :from AND :to 
    GROUP BY 1
    ORDER BY 1
    """, nativeQuery = true)
    List<MonthlyAmountRow> incomeByMonth(@Param("from") LocalDate from,
                                         @Param("to") LocalDate to);


    @Query("SELECT p FROM Invoice p WHERE LOWER(p.titulo) LIKE LOWER(CONCAT('%', :query, '%')) ")
    Page<Invoice> searchByTitulo(@Param("query") String query, Pageable pageable);

    @Query("SELECT p FROM Invoice p WHERE p.tenant.id = :tenantId AND LOWER(p.titulo) LIKE LOWER(CONCAT('%', :query, '%')) ")
    Page<Invoice> searchByTituloAndTenant(@Param("query") String query, @Param("tenantId") Long tenantId, Pageable pageable);

    @Query("SELECT p FROM Invoice p WHERE LOWER(p.titulo) LIKE LOWER(:titulo)")
    Optional<Invoice> findByTitulo(@Param("titulo") String titulo);

    @Query("SELECT p FROM Invoice p WHERE p.tenant.id = :tenantId AND LOWER(p.titulo) LIKE LOWER(:titulo)")
    Optional<Invoice> findByTituloAndTenant_Id(@Param("titulo") String titulo, @Param("tenantId") Long tenantId);


    @Query(value = """
            SELECT u.username as "userName",
                COALESCE(SUM(p.cantidad), 0) as "unitsSold",
                COALESCE(SUM(p.precio), 0) as "income"
            FROM invoices p
            JOIN usuarios u ON p.ownerid = u.id
            WHERE p.startdate >= :from AND p.startdate <= :to
            GROUP BY u.username
            """, nativeQuery = true)
    List<Map<String, Object>> getUserPerformanceData(@Param("from") LocalDate from, @Param("to") LocalDate to);

    List<Invoice> findByFechaEstimadaBetween(LocalDate today, LocalDate endOfWeek);

    List<Invoice> findTop200ByFechaEstimadaBetweenAndTenant_IdOrderByFechaEstimadaAsc(
            LocalDate today, LocalDate endOfWeek, Long tenantId);

    List<Invoice> findByStartDateBetween(LocalDate from, LocalDate to);

    List<Invoice> findByStartDateBetweenAndTenant_Id(LocalDate from, LocalDate to, Long tenantId);

   List<Invoice> findByStartDateLessThanEqualAndTenant_Id(LocalDate to, Long tenantId);

   Page<Invoice> findByTenant_Id(Long tenantId, Pageable pageable);

   Optional<Invoice> findByIdAndTenant_Id(Long id, Long tenantId);

   long countByTenant_Id(Long tenantId);


   @Query("""
               SELECT w.status, COUNT(p)
               FROM Invoice p
               JOIN WorkOrder w ON w.invoice = p
               GROUP BY w.status""")
   List<Object[]> findTopOrders();

   @Query("""
               SELECT w.status, COUNT(p)
               FROM Invoice p
               JOIN WorkOrder w ON w.invoice = p
               WHERE p.tenant.id = :tenantId
               GROUP BY w.status""")
   List<Object[]> findTopOrdersByTenant(@Param("tenantId") Long tenantId);


   List<Invoice> findByWorkOrderStatus(Status status);

   List<Invoice> findTop200ByWorkOrderStatusAndTenant_IdOrderByIdDesc(Status status, Long tenantId);

   @Query("""
           SELECT p FROM Invoice p
           LEFT JOIN FETCH p.customer c
           LEFT JOIN FETCH p.workOrder wo
           WHERE p.tenant.id = :tenantId
           AND COALESCE(p.fechaEntrega, p.fechaEstimada) < :today
           AND (wo IS NULL OR wo.status <> com.example.demo.model.Status.CERRADO)
           ORDER BY COALESCE(p.fechaEntrega, p.fechaEstimada) ASC
           """)
   List<Invoice> findOverdueOpenInvoicesByTenant(
           @Param("today") LocalDate today,
           @Param("tenantId") Long tenantId
   );

   @Query("""
           SELECT p FROM Invoice p LEFT JOIN p.workOrder wo
           WHERE (:titulo IS NULL OR LOWER(p.titulo) LIKE LOWER(CONCAT('%', :titulo, '%')))
           AND (:workOrderStatus IS NULL OR wo.status = :workOrderStatus)
           AND (:from IS NULL OR p.startDate >= :from)
           AND (:to IS NULL OR p.startDate <= :to)
           """)
   Page<Invoice> filterProducts(
           @Param("titulo") String titulo,
           @Param("workOrderStatus") Status workOrderStatus,
           @Param("from") LocalDate from,
           @Param("to") LocalDate to,
           Pageable pageable
   );

   @Query("""
           SELECT p FROM Invoice p LEFT JOIN p.workOrder wo
           WHERE p.tenant.id = :tenantId
           AND (:titulo IS NULL OR LOWER(p.titulo) LIKE LOWER(CONCAT('%', :titulo, '%')))
           AND (:status IS NULL OR wo.status = :status)
           AND (:from IS NULL OR p.startDate >= :from)
           AND (:to IS NULL OR p.startDate <= :to)
           """)
   Page<Invoice> filterProductsByTenant(
           @Param("tenantId") Long tenantId,
           @Param("titulo") String titulo,
           @Param("status") Status status,
           @Param("from") LocalDate from,
           @Param("to") LocalDate to,
           Pageable pageable
   );

}

