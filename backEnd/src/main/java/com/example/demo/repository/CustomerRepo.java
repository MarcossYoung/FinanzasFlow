package com.example.demo.repository;

import com.example.demo.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CustomerRepo extends JpaRepository<Customer, Long> {
    List<Customer> findByTenant_Id(Long tenantId);

    @Query("""
            SELECT c FROM Customer c
            WHERE c.tenant.id = :tenantId
            AND (LOWER(c.name) LIKE LOWER(CONCAT('%', :q, '%')) OR c.cuitDni LIKE CONCAT('%', :q, '%'))
            """)
    List<Customer> search(@Param("tenantId") Long tenantId, @Param("q") String q);

    Optional<Customer> findByPhone(String phone);
}
