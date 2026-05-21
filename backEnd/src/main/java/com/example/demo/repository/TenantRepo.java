package com.example.demo.repository;

import com.example.demo.model.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantRepo extends JpaRepository<Tenant, Long> {
    Optional<Tenant> findBySlug(String slug);
}
