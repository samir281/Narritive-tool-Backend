package com.narrativenews.repo;

import com.narrativenews.domain.Edition;
import com.narrativenews.domain.Enums.EditionStatus;
import com.narrativenews.domain.Enums.PdfStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EditionRepository extends JpaRepository<Edition, UUID> {
    Optional<Edition> findByEditionDate(LocalDate date);
    Optional<Edition> findFirstByStatusOrderByEditionDateDesc(EditionStatus status);
    Optional<Edition> findFirstByStatusAndEditionDateLessThanOrderByEditionDateDesc(EditionStatus status, LocalDate date);
    Optional<Edition> findFirstByStatusAndEditionDateGreaterThanOrderByEditionDateAsc(EditionStatus status, LocalDate date);
    List<Edition> findByStatusAndEditionDateBetweenOrderByEditionDate(EditionStatus status, LocalDate from, LocalDate to);
    List<Edition> findByEditionDateBetweenOrderByEditionDate(LocalDate from, LocalDate to);
    List<Edition> findByPdfStatus(PdfStatus status);
}
