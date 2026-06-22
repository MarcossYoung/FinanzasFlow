package com.example.demo.repository;


import com.example.demo.model.Status;
import com.example.demo.model.WorkOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkOrderRepo extends JpaRepository<WorkOrder, Long> {
    @Query("""
    SELECT w 
    FROM WorkOrder w 
    JOIN FETCH w.invoice p
    WHERE w.status = :status
""")
    List<WorkOrder> findByStatus(@Param("status") Status status);

    @Query("""
    SELECT w
    FROM WorkOrder w
    JOIN FETCH w.invoice p
    WHERE p.tenant.id = :tenantId
    AND w.status = :status
""")
    List<WorkOrder> findByTenantIdAndStatus(@Param("tenantId") Long tenantId, @Param("status") Status status);

    Optional<WorkOrder> findByInvoice_Id(Long invoiceId);


    @Query("""
    SELECT COUNT(w)
    FROM WorkOrder w
    JOIN w.invoice p
    WHERE p.fechaEntrega BETWEEN :today AND :endOfWeek
""")
    long countFechaEntrega(@Param("today") LocalDate today, @Param("endOfWeek") LocalDate endOfWeek);

    @Query("""
    SELECT COUNT(w)
    FROM WorkOrder w
    JOIN w.invoice p
    WHERE p.tenant.id = :tenantId
    AND p.fechaEntrega BETWEEN :today AND :endOfWeek
""")
    long countFechaEntregaByTenant(@Param("tenantId") Long tenantId,
                                   @Param("today") LocalDate today,
                                   @Param("endOfWeek") LocalDate endOfWeek);

    @Query("""
    SELECT w.status, COUNT(w)
    FROM WorkOrder w
    JOIN w.invoice p
    WHERE p.tenant.id = :tenantId
    GROUP BY w.status
""")
    List<Object[]> countOrdersByStatusForTenant(@Param("tenantId") Long tenantId);


}
