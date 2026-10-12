package com.narrativenews.repo;

import com.narrativenews.domain.Reader;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ReaderRepository extends JpaRepository<Reader, UUID> {
    Optional<Reader> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
}
