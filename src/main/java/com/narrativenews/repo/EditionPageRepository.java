package com.narrativenews.repo;

import com.narrativenews.domain.EditionPage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EditionPageRepository extends JpaRepository<EditionPage, UUID> {
    List<EditionPage> findByEditionIdOrderByPageNo(UUID editionId);
    Optional<EditionPage> findByEditionIdAndPageNo(UUID editionId, int pageNo);
    void deleteByEditionId(UUID editionId);
}
