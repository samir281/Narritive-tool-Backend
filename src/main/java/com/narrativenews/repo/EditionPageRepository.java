package com.narrativenews.repo;

import com.narrativenews.domain.EditionPage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EditionPageRepository extends JpaRepository<EditionPage, UUID> {
    List<EditionPage> findByEditionIdOrderByPageNo(UUID editionId);
    Optional<EditionPage> findByEditionIdAndPageNo(UUID editionId, int pageNo);

    @Query("select p from EditionPage p where not exists (select t from PageText t where t.pageId = p.id) order by p.editionId, p.pageNo")
    List<EditionPage> findWithoutText();
    void deleteByEditionId(UUID editionId);
}
