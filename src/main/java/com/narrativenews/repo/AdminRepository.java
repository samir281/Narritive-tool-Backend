package com.narrativenews.repo;

import com.narrativenews.domain.Admin;
import com.narrativenews.domain.Enums.AccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface AdminRepository extends JpaRepository<Admin, UUID> {
    Optional<Admin> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    long countByStatus(AccountStatus status);
}
