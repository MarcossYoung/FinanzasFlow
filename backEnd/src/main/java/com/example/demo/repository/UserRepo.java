package com.example.demo.repository;

import com.example.demo.model.AppUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;


@Repository
public interface UserRepo extends JpaRepository<AppUser, Long> {
     AppUser getById(Long id);

     Optional<AppUser> findByUsername(String username);

    Page<AppUser> findAll(Pageable pageable);


    boolean existsByUsername(String username);

    @Modifying
    @Transactional
    @Query(value = "UPDATE usuarios SET app_user_role = 'GESTOR' WHERE app_user_role IN ('USER', 'SELLER', 'VIEWER')", nativeQuery = true)
    void normalizeClientRoles();

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM usuarios WHERE username = 'viewer'", nativeQuery = true)
    void deleteViewerUsers();
}
